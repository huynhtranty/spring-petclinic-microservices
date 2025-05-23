pipeline {
    agent any

    environment {
        DOCKER_HUB_REPO = 'hytaty'
        // XÓA VALID_SERVICES KHỎI ĐÂY
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
                    // DI CHUYỂN VALID_SERVICES VÀO ĐÂY
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

                    def servicesFound = findFiles(glob: '*/pom.xml')

                    def serviceNames = servicesFound.collect { fileWrapper ->
                        return fileWrapper.path.split('/')[0]
                    }.unique()

                    def servicesToProcess = serviceNames.findAll { serviceName ->
                        VALID_SERVICES.contains(serviceName)
                    }

                    withCredentials([usernamePassword(credentialsId: 'docker-hub-pat-hytaty', usernameVariable: 'DOCKER_USER', passwordVariable: 'DOCKER_PASS')]) {
                        sh "echo \$DOCKER_PASS | docker login -u \$DOCKER_USER --password-stdin"

                        for (def serviceName : servicesToProcess) {
                            def baseImageName = "${DOCKER_HUB_REPO}/${serviceName.toLowerCase()}"

                            def sourceImageTag = "latest"

                            def imageWithCommitTag = "${baseImageName}:${env.COMMIT_ID}"
                            def imageWithBranchTag = "${baseImageName}:${env.BRANCH_NAME_CLEAN}"

                            echo "--- Processing image for ${serviceName} ---"
                            echo "Source Image: ${baseImageName}:${sourceImageTag}"
                            echo "Target Commit Tag: ${imageWithCommitTag}"
                            echo "Target Branch Tag: ${imageWithBranchTag}"

                            sh "docker tag ${baseImageName}:${sourceImageTag} ${imageWithCommitTag}"
                            sh "docker push ${imageWithCommitTag}"
                            echo "Pushed: ${imageWithCommitTag}"

                            if (env.BRANCH_NAME_CLEAN != sourceImageTag) {
                                sh "docker tag ${baseImageName}:${sourceImageTag} ${imageWithBranchTag}"
                                sh "docker push ${imageWithBranchTag}"
                                echo "Pushed: ${imageWithBranchTag}"
                            }

                            if ("${env.BRANCH_NAME_CLEAN}" == "main") {
                                if (sourceImageTag != "latest") {
                                    sh "docker tag ${baseImageName}:${sourceImageTag} ${baseImageName}:latest"
                                    sh "docker push ${baseImageName}:latest"
                                    echo "Pushed: ${baseImageName}:latest"
                                }
                                if (sourceImageTag != "main") {
                                    sh "docker tag ${baseImageName}:${sourceImageTag} ${baseImageName}:main"
                                    sh "docker push ${baseImageName}:main"
                                    echo "Pushed: ${baseImageName}:main"
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