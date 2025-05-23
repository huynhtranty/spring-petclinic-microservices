pipeline {
    agent any

    environment {
        DOCKER_HUB_REPO = 'hytaty'
        // Không cần SERVICE_NAME cố định ở đây vì chúng ta sẽ duyệt qua từng service
        # DOCKER_HUB_CREDENTIALS_ID_VAR = 'docker-hub-pat-hytaty' // Nếu dùng lựa chọn 2
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
                    // Maven sẽ tự động tạo images và đặt tên theo cấu hình trong pom.xml của từng service
                    sh '''
                        echo "Building all microservices with Maven buildDocker profile..."
                        chmod +x mvnw
                        ./mvnw clean install -P buildDocker -DskipTests
                    '''
                    
                    // Lấy danh sách các microservice từ thư mục hiện tại
                    // Giả định mỗi microservice là một thư mục con chứa pom.xml
                    def services = findFiles(glob: '*/pom.xml').collect { it.parent }
                    // Lọc ra các thư mục không phải là service chính (ví dụ: .git, scripts, etc.)
                    // Bạn có thể cần điều chỉnh regex này tùy thuộc vào tên thư mục service của bạn
                    services = services.findAll { 
                        it.matches(~/^(api-gateway|config-server|discovery-server|vets-service|visits-service|customers-service|tracing-server|admin-server)$/) 
                    }

                    // Đăng nhập Docker Hub
                    withCredentials([usernamePassword(credentialsId: 'docker-hub', usernameVariable: 'DOCKER_USER', passwordVariable: 'DOCKER_PASS')]) {
                        sh "echo \$DOCKER_PASS | docker login -u \$DOCKER_USER --password-stdin"

                        for (def serviceDir : services) {
                            def serviceName = serviceDir.split('/')[-1] // Lấy tên thư mục cuối cùng
                            def baseImageName = "${DOCKER_HUB_REPO}/${serviceName.toLowerCase()}" // Đảm bảo tên repo là chữ thường

                            // Maven -PbuildDocker thường sẽ tạo image với tag 'latest' hoặc version từ pom.xml
                            // Chúng ta sẽ retag và push theo yêu cầu của bạn

                            def sourceImageTag = "latest" // Thường là 'latest' hoặc '0.0.1-SNAPSHOT' nếu bạn không config lại Maven
                                                          // Có thể cần kiểm tra pom.xml của từng service để biết tag mặc định
                                                          // hoặc lấy version từ pom.xml:
                                                          // def version = new XmlParser().parseText(readFile("${serviceDir}/pom.xml")).version.text()
                                                          // def sourceImageTag = version

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