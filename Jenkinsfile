pipeline {
    agent any

    environment {
        DOCKER_HUB_REPO = 'hytaty'
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
                    sh '''
                        echo "Building all microservices with Maven buildDocker profile..."
                        chmod +x mvnw
                        ./mvnw clean install -P buildDocker -DskipTests
                    '''

                    // Lấy danh sách các microservice từ thư mục hiện tại
                    // Sử dụng `path` của FileWrapper và trích xuất thư mục cha
                    def services = findFiles(glob: '*/pom.xml').collect { 
                        // Lấy đường dẫn đầy đủ (e.g., "api-gateway/pom.xml")
                        def fullPath = it.path 
                        // Tách lấy phần thư mục (e.g., "api-gateway")
                        def serviceDir = fullPath.split('/')[0] 
                        return serviceDir
                    }
                    
                    // Lọc ra các thư mục không phải là service chính
                    // Vẫn cần regex này để đảm bảo chỉ xử lý các microservice mong muốn
                    services = services.findAll { 
                        it.matches(~/^(api-gateway|config-server|discovery-server|vets-service|visits-service|customers-service|tracing-server|admin-server)$/) 
                    }
                    // Loại bỏ các service trùng lặp nếu có (collect có thể trả về trùng nếu có pom.xml ở nhiều nơi)
                    services = services.unique()


                    // Đăng nhập Docker Hub
                    withCredentials([usernamePassword(credentialsId: 'docker-hub-pat-hytaty', usernameVariable: 'DOCKER_USER', passwordVariable: 'DOCKER_PASS')]) {
                        sh "echo \$DOCKER_PASS | docker login -u \$DOCKER_USER --password-stdin"

                        for (def serviceName : services) { // serviceName giờ đã là tên thư mục vd: "api-gateway"
                            def baseImageName = "${DOCKER_HUB_REPO}/${serviceName.toLowerCase()}"

                            def sourceImageTag = "latest" // Kiểm tra lại pom.xml để xác nhận nếu không phải 'latest'
                                                          // (ví dụ: `new XmlParser().parseText(readFile("${serviceName}/pom.xml")).version.text()`)

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
                            if (env.BRANCH_NAME_CLEAN != sourceImageTag) {
                                sh "docker tag ${baseImageName}:${sourceImageTag} ${imageWithBranchTag}"
                                sh "docker push ${imageWithBranchTag}"
                                echo "Pushed: ${imageWithBranchTag}"
                            }

                            // Push 'latest' và 'main' nếu đang ở branch 'main'
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