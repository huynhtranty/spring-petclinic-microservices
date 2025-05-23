pipeline {
    agent any

    environment {
        // Tên Docker Hub repository của bạn (ví dụ: your_dockerhub_username)
        DOCKER_HUB_REPO = 'hytaty'

        // Đường dẫn tới Dockerfile chung của bạn, nằm trong thư mục 'docker'
        DOCKERFILE_PATH = 'docker/Dockerfile'

        // Phiên bản của dự án (Lấy từ pom.xml gốc)
        // Cần thiết để tạo ARTIFACT_NAME đúng cho Dockerfile
        // ĐẢM BẢO GIÁ TRỊ NÀY KHỚP VỚI <version> TRONG pom.xml GỐC CỦA BẠN
        PROJECT_VERSION = '3.4.1' 

        // Định nghĩa cổng exposed cho mỗi service (Dựa vào docker-compose.yml của bạn)
        // Đây là các cổng mà ứng dụng thực sự lắng nghe bên trong container
        SERVICE_PORTS = [
            'spring-petclinic-admin-server': '9090',
            'spring-petclinic-api-gateway': '8080',
            'spring-petclinic-config-server': '8888',
            'spring-petclinic-customers-service': '8081',
            'spring-petclinic-discovery-server': '8761',
            'spring-petclinic-genai-service': '8084',
            'spring-petclinic-vets-service': '8083',
            'spring-petclinic-visits-service': '8082'
        ]
    }

    stages {
        stage('Checkout') {
            steps {
                script {
                    checkout scm // Checkout mã nguồn của branch hiện tại

                    // Lấy ID commit cuối cùng của branch hiện tại (dùng ID ngắn)
                    env.COMMIT_ID = sh(returnStdout: true, script: 'git rev-parse --short HEAD').trim()

                    // Lấy tên branch, loại bỏ các ký tự đặc biệt để dùng làm tag Docker
                    env.BRANCH_NAME_CLEAN = env.BRANCH_NAME ? env.BRANCH_NAME.replaceAll('[^a-zA-Z0-9.-]+', '_') : 'main'

                    echo "--- Starting CI Pipeline for microservices ---"
                    echo "Current Branch: ${env.BRANCH_NAME}"
                    echo "Commit ID: ${env.COMMIT_ID}"
                }
            }
        }

        stage('Build All JARs') {
            steps {
                script {
                    sh '''
                        echo "Building all microservices JARs with Maven..."
                        chmod +x mvnw
                        # Lệnh này sẽ build JAR cho TẤT CẢ các microservice
                        # và đặt chúng vào thư mục 'target' của từng module con.
                        ./mvnw clean install -DskipTests
                    '''
                    echo "All microservices JARs built successfully."
                }
            }
        }

        stage('Build and Push Docker Images') {
            steps {
                script {
                    // Định nghĩa danh sách các microservice hợp lệ
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

                    // Lấy danh sách các service thực sự có trong project dựa vào pom.xml của chúng
                    def servicesFound = findFiles(glob: '*/pom.xml')
                    def projectServiceNames = servicesFound.collect { fileWrapper ->
                        return fileWrapper.path.split('/')[0]
                    }.unique()

                    // Lọc ra chỉ những service nằm trong danh sách VALID_SERVICES và thực sự có trong project
                    def servicesToProcess = projectServiceNames.findAll { serviceName ->
                        VALID_SERVICES.contains(serviceName)
                    }

                    // Đăng nhập Docker Hub một lần duy nhất trước khi push các image
                    withCredentials([usernamePassword(credentialsId: 'docker-hub', usernameVariable: 'DOCKER_USER', passwordVariable: 'DOCKER_PASS')]) { // Sửa 'docker-hub' thành 'docker-hub-pat-hytaty' nếu đó là ID credentials của bạn
                        sh "echo \$DOCKER_PASS | docker login -u \$DOCKER_USER --password-stdin"
                        echo "Logged into Docker Hub."

                        // Lặp qua từng service để build và push image của nó
                        for (def serviceName : servicesToProcess) {
                            // Tên file JAR đã build (ví dụ: spring-petclinic-admin-server-3.4.1.jar)
                            def artifactName = "${serviceName}-${env.PROJECT_VERSION}"
                            
                            // Cổng exposed cho service này
                            def exposedPort = env.SERVICE_PORTS[serviceName]
                            if (!exposedPort) {
                                error "Error: Port not defined for service ${serviceName} in SERVICE_PORTS map. Please update Jenkinsfile."
                            }

                            // Tên image cuối cùng trên Docker Hub (ví dụ: hytaty/spring-petclinic-admin-server)
                            def targetImageRepo = "${env.DOCKER_HUB_REPO}/${serviceName.toLowerCase()}"

                            // Tag dựa trên commit ID cuối cùng
                            def commitIdTag = env.COMMIT_ID

                            echo "--- Processing image for ${serviceName} ---"
                            echo "Building image: ${targetImageRepo}:${commitIdTag}"
                            echo "Using Dockerfile: ${env.DOCKERFILE_PATH}"
                            echo "ARTIFACT_NAME: ${artifactName}, EXPOSED_PORT: ${exposedPort}"

                            // Lệnh docker build để tạo image cho service này
                            // Context build là thư mục gốc của dự án (.), để có thể COPY các JAR từ các thư mục con
                            // Truyền ARTIFACT_NAME và EXPOSED_PORT làm build-args cho Dockerfile
                            sh "docker build -t ${targetImageRepo}:${commitIdTag} " +
                               "-f ${env.DOCKERFILE_PATH} " +
                               "--build-arg ARTIFACT_NAME=${artifactName} " + // ĐÂY LÀ DÒNG QUAN TRỌNG ĐỂ KHẮC PHỤC LỖI
                               "--build-arg EXPOSED_PORT=${exposedPort} ."
                            
                            echo "Built: ${targetImageRepo}:${commitIdTag}"

                            echo "--- Pushing image to Docker Hub ---"
                            sh "docker push ${targetImageRepo}:${commitIdTag}"
                            echo "Pushed: ${targetImageRepo}:${commitIdTag}"

                            // Nếu branch hiện tại là 'main', push thêm tag 'latest' và 'main'
                            if ("${env.BRANCH_NAME_CLEAN}" == "main") {
                                echo "Branch is 'main', pushing 'latest' and 'main' tags as well."

                                // Push 'latest' tag
                                sh "docker tag ${targetImageRepo}:${commitIdTag} ${targetImageRepo}:latest"
                                sh "docker push ${targetImageRepo}:latest"
                                echo "Pushed: ${targetImageRepo}:latest"

                                // Push 'main' tag (nếu không trùng với latest hoặc commitId)
                                if (commitIdTag != "main") { // Tránh push trùng tag nếu commitId vô tình là "main"
                                   sh "docker tag ${targetImageRepo}:${commitIdTag} ${targetImageRepo}:main"
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
            sh "docker logout" // Đảm bảo logout khỏi Docker Hub sau khi hoàn tất
            cleanWs() // Dọn dẹp workspace Jenkins
        }
        success {
            echo "CI Pipeline completed successfully for all microservices images."
        }
        failure {
            echo "CI Pipeline failed for microservices images build/push."
        }
    }
}