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
                    echo "--- Building all microservices Docker images ---"
                    echo "Branch: ${env.BRANCH_NAME_CLEAN}, Commit: ${env.COMMIT_ID}"
                }
            }
        }

        stage('Build and Push All Docker Images') {
            steps {
                script {
                    // Danh sách các service hợp lệ (đặt trong script block để tránh lỗi DSL)
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
                        # Chạy lệnh này ở thư mục gốc của project
                        # Maven sẽ đi vào từng module con và build Docker image cho từng cái
                        ./mvnw clean install -P buildDocker -DskipTests
                    '''

                    // Lấy danh sách các microservice từ thư mục hiện tại
                    // `findFiles` sẽ trả về danh sách các file pom.xml trong các thư mục con của service
                    def servicesFound = findFiles(glob: '*/pom.xml')

                    // Trích xuất tên thư mục cha (là tên service đầy đủ như trong pom.xml)
                    def serviceNames = servicesFound.collect { fileWrapper ->
                        return fileWrapper.path.split('/')[0] // Ví dụ: "spring-petclinic-admin-server"
                    }.unique() // Đảm bảo không có tên dịch vụ trùng lặp

                    // Lọc ra chỉ những service nằm trong danh sách VALID_SERVICES
                    def servicesToProcess = serviceNames.findAll { serviceName ->
                        VALID_SERVICES.contains(serviceName)
                    }

                    // Đăng nhập Docker Hub một lần
                    withCredentials([usernamePassword(credentialsId: 'docker-hub', usernameVariable: 'DOCKER_USER', passwordVariable: 'DOCKER_PASS')]) {
                        sh "echo \$DOCKER_PASS | docker login -u \$DOCKER_USER --password-stdin"

                        // Lặp qua từng service để retag và push image
                        for (def serviceName : servicesToProcess) {
                            // Tên image được build bởi Maven trên Docker daemon cục bộ
                            // Dựa trên pom.xml, nó là ${docker.image.prefix}/${project.artifactId}
                            def localImageRef = "${LOCAL_DOCKER_ORG}/${serviceName.toLowerCase()}"

                            // Tên image đích khi push lên Docker Hub của bạn
                            def targetImageRepo = "${DOCKER_HUB_REPO}/${serviceName.toLowerCase()}"

                            def sourceImageTag = "latest" // Mặc định Maven plugin build với tag 'latest'

                            def imageWithCommitTag = "${targetImageRepo}:${env.COMMIT_ID}"
                            def imageWithBranchTag = "${targetImageRepo}:${env.BRANCH_NAME_CLEAN}"

                            echo "--- Processing image for ${serviceName} ---"
                            echo "Local Image: ${localImageRef}:${sourceImageTag}"
                            echo "Target Commit Tag: ${imageWithCommitTag}"
                            echo "Target Branch Tag: ${imageWithBranchTag}"

                            // Retag image từ localImageRef sang targetImageRepo với Commit ID
                            sh "docker tag ${localImageRef}:${sourceImageTag} ${imageWithCommitTag}"
                            sh "docker push ${imageWithCommitTag}"
                            echo "Pushed: ${imageWithCommitTag}"

                            // Retag và Push image với Branch Name
                            // Đảm bảo không push trùng tag nếu branch clean trùng với source tag
                            if (env.BRANCH_NAME_CLEAN != sourceImageTag) {
                                sh "docker tag ${localImageRef}:${sourceImageTag} ${imageWithBranchTag}"
                                sh "docker push ${imageWithBranchTag}"
                                echo "Pushed: ${imageWithBranchTag}"
                            }

                            // Push 'latest' và 'main' nếu đang ở branch 'main'
                            if ("${env.BRANCH_NAME_CLEAN}" == "main") {
                                // Tránh push trùng tag nếu sourceImageTag đã là latest
                                if (sourceImageTag != "latest") {
                                    sh "docker tag ${localImageRef}:${sourceImageTag} ${targetImageRepo}:latest"
                                    sh "docker push ${targetImageRepo}:latest"
                                    echo "Pushed: ${targetImageRepo}:latest"
                                }
                                // Tránh push trùng tag nếu sourceImageTag đã là main (nếu có)
                                if (sourceImageTag != "main") {
                                    sh "docker tag ${localImageRef}:${sourceImageTag} ${targetImageRepo}:main"
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
            echo "CI Pipeline completed successfully for all microservices images."
        }
        failure {
            echo "CI Pipeline failed for microservices images build/push."
        }
    }
}