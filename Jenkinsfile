pipeline {
    agent any

    environment {
        DOCKER_HUB_REPO = 'hytaty' // Repository của bạn trên Docker Hub
        LOCAL_DOCKER_ORG = 'springcommunity' // Tổ chức mà Maven plugin đang sử dụng để build image
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
                script {
                    env.COMMIT_ID = sh(returnStdout: true, script: 'git rev-parse --short HEAD').trim()
                    env.BRANCH_NAME_CLEAN = env.BRANCH_NAME ? env.BRANCH_NAME.replaceAll('[^a-zA-Z0-9.-]+', '_') : 'main'
                    echo "--- Building all microservices ---"
                    echo "Branch: ${env.BRANCH_NAME_CLEAN}, Commit: ${env.COMMIT_ID}"
                }
            }
        }

        stage('Build and Push All Docker Images') {
            steps {
                script {
                    def VALID_SERVICES = [
                        'spring-petclinic-admin-server',
                        'spring-petclinic-api-gateway',
                        'spring-petclinic-config-server',
                        'spring-petclinic-customers-service',
                        'spring-petclinic-discovery-server',
                        'spring-petclinic-genai-service',
                        'spring-petclinic-vets-service',
                        'spring-petclinic-visits-service'
                    ]

                    sh '''
                        echo "Building all microservices with Maven buildDocker profile..."
                        chmod +x mvnw
                        ./mvnw clean install -P buildDocker -DskipTests
                    '''
                    // Không cần `docker images` ở đây nữa, chúng ta đã xác định được pattern
                    // Nếu bạn muốn giữ lại để debug trong tương lai, hãy cứ để nó.

                    def servicesFound = findFiles(glob: '*/pom.xml')

                    def serviceNames = servicesFound.collect { fileWrapper ->
                        return fileWrapper.path.split('/')[0]
                    }.unique()

                    def servicesToProcess = serviceNames.findAll { serviceName ->
                        VALID_SERVICES.contains(serviceName)
                    }

                    withCredentials([usernamePassword(credentialsId: 'docker-hub', usernameVariable: 'DOCKER_USER', passwordVariable: 'DOCKER_PASS')]) {
                        sh "echo \$DOCKER_PASS | docker login -u \$DOCKER_USER --password-stdin"

                        for (def serviceName : servicesToProcess) {
                            // Tên image được build bởi Maven trên Docker daemon cục bộ
                            def localImageName = "${LOCAL_DOCKER_ORG}/${serviceName.toLowerCase()}" // VD: springcommunity/spring-petclinic-admin-server

                            // Tên image đích khi push lên Docker Hub của bạn
                            def targetImageRepo = "${DOCKER_HUB_REPO}/${serviceName.toLowerCase()}" // VD: hytaty/spring-petclinic-admin-server

                            def sourceImageTag = "latest" // Maven plugin build với tag 'latest'

                            def imageWithCommitTag = "${targetImageRepo}:${env.COMMIT_ID}"
                            def imageWithBranchTag = "${targetImageRepo}:${env.BRANCH_NAME_CLEAN}"

                            echo "--- Processing image for ${serviceName} ---"
                            echo "Local Image: ${localImageName}:${sourceImageTag}"
                            echo "Target Commit Tag: ${imageWithCommitTag}"
                            echo "Target Branch Tag: ${imageWithBranchTag}"

                            // Retag image từ localImageName sang targetImageRepo với Commit ID
                            sh "docker tag ${localImageName}:${sourceImageTag} ${imageWithCommitTag}"
                            sh "docker push ${imageWithCommitTag}"
                            echo "Pushed: ${imageWithCommitTag}"

                            // Retag và Push image với Branch Name
                            // Đảm bảo không push trùng tag nếu branch clean trùng với source tag
                            if (env.BRANCH_NAME_CLEAN != sourceImageTag) {
                                sh "docker tag ${localImageName}:${sourceImageTag} ${imageWithBranchTag}"
                                sh "docker push ${imageWithBranchTag}"
                                echo "Pushed: ${imageWithBranchTag}"
                            }

                            // Push 'latest' và 'main' nếu đang ở branch 'main'
                            if ("${env.BRANCH_NAME_CLEAN}" == "main") {
                                // Tránh push trùng tag nếu sourceImageTag đã là latest
                                if (sourceImageTag != "latest") {
                                    sh "docker tag ${localImageName}:${sourceImageTag} ${targetImageRepo}:latest"
                                    sh "docker push ${targetImageRepo}:latest"
                                    echo "Pushed: ${targetImageRepo}:latest"
                                }
                                // Tránh push trùng tag nếu sourceImageTag đã là main
                                if (sourceImageTag != "main") {
                                    sh "docker tag ${localImageName}:${sourceImageTag} ${targetImageRepo}:main"
                                    sh "docker push ${targetImageRepo}:main"
                                    echo "Pushed: ${targetImageRepo}:main"
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    post {
        always {
            sh "docker logout"
            cleanWs()
        }
        success {
            echo "CI Pipeline completed successfully for all microservices."
        }
        failure {
            echo "CI Pipeline failed for microservices build/push."
        }
    }
}