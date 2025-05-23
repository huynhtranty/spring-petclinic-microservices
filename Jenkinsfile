pipeline {
    agent any

    environment {
        DOCKER_HUB_REPO = 'hytaty' // Repository của bạn trên Docker Hub
        
        PROJECT_IMAGE_NAME = 'spring-petclinic' // Tên hình ảnh tổng hợp cuối cùng của bạn
        
        DOCKERFILE_PATH = 'docker/Dockerfile'
        DOCKERFILE_DIR = 'docker' // Đây là build context, thư mục chứa Dockerfile và script
    }

    stages {
        stage('Checkout') {
            steps {
                script {
                    checkout scm // Checkout mã nguồn
                    env.COMMIT_ID = sh(returnStdout: true, script: 'git rev-parse --short HEAD').trim()
                    env.BRANCH_NAME_CLEAN = env.BRANCH_NAME ? env.BRANCH_NAME.replaceAll('[^a-zA-Z0-9.-]+', '_') : 'main'
                    echo "--- Starting CI Pipeline for the combined microservices image ---"
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
                        # Và đặt chúng vào thư mục 'target' của từng module con,
                        # nơi mà Dockerfile của bạn có thể COPY.
                        ./mvnw clean install -DskipTests
                    '''
                    echo "All microservices JARs built successfully."
                }
            }
        }

        stage('Build and Push Combined Docker Image') {
            steps {
                script {
                    def imageFullName = "${DOCKER_HUB_REPO}/${PROJECT_IMAGE_NAME.toLowerCase()}" // hytaty/spring-petclinic-monolith
                    def commitIdTag = env.COMMIT_ID

                    echo "--- Building combined Docker image from Dockerfile ---"
                    # Chạy lệnh docker build từ thư mục gốc của Jenkins workspace.
                    # Build context là thư mục gốc của dự án (.), để Docker có thể COPY các JAR từ các thư mục con.
                    # Đường dẫn tới Dockerfile là tương đối từ thư mục gốc.
                    sh "docker build -t ${imageFullName}:${commitIdTag} -f ${DOCKERFILE_PATH} ."
                    echo "Combined image built: ${imageFullName}:${commitIdTag}"

                    withCredentials([usernamePassword(credentialsId: 'docker-hub', usernameVariable: 'DOCKER_USER', passwordVariable: 'DOCKER_PASS')]) {
                        sh "echo \$DOCKER_PASS | docker login -u \$DOCKER_USER --password-stdin"
                        echo "Logged into Docker Hub."

                        echo "--- Pushing combined image to Docker Hub ---"
                        # Push image với tag commit ID
                        sh "docker push ${imageFullName}:${commitIdTag}"
                        echo "Pushed: ${imageFullName}:${commitIdTag}"

                        # Nếu là branch 'main', push thêm tag 'latest' và 'main'
                        if ("${env.BRANCH_NAME_CLEAN}" == "main") {
                            echo "Branch is 'main', pushing 'latest' and 'main' tags as well."
                            sh "docker tag ${imageFullName}:${commitIdTag} ${imageFullName}:latest"
                            sh "docker push ${imageFullName}:latest"
                            echo "Pushed: ${imageFullName}:latest"

                            sh "docker tag ${imageFullName}:${commitIdTag} ${imageFullName}:main"
                            sh "docker push ${imageFullName}:main"
                            echo "Pushed: ${imageFullName}:main"
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
            echo "CI Pipeline completed successfully for the combined microservices image."
        }
        failure {
            echo "CI Pipeline failed for the combined microservices image build/push."
        }
    }
}