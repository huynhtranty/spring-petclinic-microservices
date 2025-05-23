pipeline {
    agent any

    environment {
        DOCKER_HUB_REPO = 'hytaty'
        // Cập nhật danh sách các service hợp lệ với tên đầy đủ
        VALID_SERVICES = [
            'spring-petclinic-admin-server',
            'spring-petclinic-api-gateway',
            'spring-petclinic-config-server',
            'spring-petclinic-customers-service',
            'spring-petclinic-discovery-server',
            'spring-petclinic-genai-service',
            'spring-petclinic-vets-service',
            'spring-petclinic-visits-service'
        ]
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
                    // Chạy Maven command để build JAR và Docker images cho TẤT CẢ các service
                    sh '''
                        echo "Building all microservices with Maven buildDocker profile..."
                        chmod +x mvnw
                        ./mvnw clean install -P buildDocker -DskipTests
                    '''

                    // Lấy danh sách các microservice từ thư mục hiện tại
                    // `findFiles` sẽ trả về danh sách các file pom.xml
                    def servicesFound = findFiles(glob: '*/pom.xml')

                    // Trích xuất tên thư mục cha (là tên service đầy đủ)
                    def serviceNames = servicesFound.collect { fileWrapper ->
                        // it.path sẽ là ví dụ: "spring-petclinic-api-gateway/pom.xml"
                        // fileWrapper.path.split('/')[0] sẽ lấy "spring-petclinic-api-gateway"
                        return fileWrapper.path.split('/')[0]
                    }.unique() // Đảm bảo không có tên dịch vụ trùng lặp

                    // Lọc ra chỉ những service nằm trong danh sách VALID_SERVICES
                    def servicesToProcess = serviceNames.findAll { serviceName ->
                        VALID_SERVICES.contains(serviceName)
                    }

                    // Đăng nhập Docker Hub
                    withCredentials([usernamePassword(credentialsId: 'docker-hub-pat-hytaty', usernameVariable: 'DOCKER_USER', passwordVariable: 'DOCKER_PASS')]) {
                        sh "echo \$DOCKER_PASS | docker login -u \$DOCKER_USER --password-stdin"

                        for (def serviceName : servicesToProcess) {
                            // Tên repository trên Docker Hub phải là chữ thường
                            def baseImageName = "${DOCKER_HUB_REPO}/${serviceName.toLowerCase()}"

                            // Profile Maven `buildDocker` mặc định tạo image với tag 'latest'
                            def sourceImageTag = "latest"

                            def imageWithCommitTag = "${baseImageName}:${env.COMMIT_ID}"
                            def imageWithBranchTag = "${baseImageName}:${env.BRANCH_NAME_CLEAN}"

                            echo "--- Processing image for ${serviceName} ---"
                            echo "Source Image: ${baseImageName}:${sourceImageTag}"
                            echo "Target Commit Tag: ${imageWithCommitTag}"
                            echo "Target Branch Tag: ${imageWithBranchTag}"

                            // Retag và Push image với Commit ID
                            sh "docker tag ${baseImageName}:${sourceImageTag} ${imageWithCommitTag}"
                            sh "docker push ${imageWithCommitTag}"
                            echo "Pushed: ${imageWithCommitTag}"

                            // Retag và Push image với Branch Name
                            if (env.BRANCH_NAME_CLEAN != sourceImageTag) { // Tránh push trùng tag nếu branch clean trùng với source tag
                                sh "docker tag ${baseImageName}:${sourceImageTag} ${imageWithBranchTag}"
                                sh "docker push ${imageWithBranchTag}"
                                echo "Pushed: ${imageWithBranchTag}"
                            }

                            // Push 'latest' và 'main' nếu đang ở branch 'main'
                            if ("${env.BRANCH_NAME_CLEAN}" == "main") {
                                if (sourceImageTag != "latest") { // Tránh push trùng tag nếu sourceImageTag đã là latest
                                    sh "docker tag ${baseImageName}:${sourceImageTag} ${baseImageName}:latest"
                                    sh "docker push ${baseImageName}:latest"
                                    echo "Pushed: ${baseImageName}:latest"
                                }
                                if (sourceImageTag != "main") { // Tránh push trùng tag nếu sourceImageTag đã là main
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