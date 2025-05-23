pipeline {
    agent any

    environment {
        DOCKER_HUB_REPO = 'hytaty'
        SERVICE_NAME = env.JOB_NAME.split('/')[0].replace('petclinic-', '').toLowerCase()
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
                script {
                    env.COMMIT_ID = sh(returnStdout: true, script: 'git rev-parse --short HEAD').trim()
                    env.BRANCH_NAME_CLEAN = env.BRANCH_NAME ? env.BRANCH_NAME.replaceAll('[^a-zA-Z0-9.-]+', '_') : 'main'
                    echo "Building ${env.SERVICE_NAME} - Branch: ${env.BRANCH_NAME_CLEAN}, Commit: ${env.COMMIT_ID}"
                }
            }
        }

        stage('Build Application') {
            steps {
                sh '''
                    echo "Building Spring Boot application..."
                    chmod +x mvnw
                    ./mvnw clean package -DskipTests
                '''
            }
        }

        stage('Build Docker Image') {
            steps {
                script {
                    def imageTag = env.COMMIT_ID

                    // SỬA ĐỔI Ở ĐÂY: Dùng ''' thay vì """ để tránh interpolation của Groovy
                    sh '''
                        echo "Building Docker image with tag: ${imageTag}"

                        if [ ! -f Dockerfile ]; then
                            cat > Dockerfile << 'EOF'
FROM openjdk:17-jdk-slim
VOLUME /tmp
ARG JAR_FILE=target/*.jar
COPY ${JAR_FILE} app.jar
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app.jar"]
EOF
                        fi

                        # Build image with commit ID tag
                        docker build -t ''' + "${DOCKER_HUB_REPO}/${SERVICE_NAME}:${imageTag}" + ''' .

                        # Also tag with branch name for convenience
                        docker tag ''' + "${DOCKER_HUB_REPO}/${SERVICE_NAME}:${imageTag} ${DOCKER_HUB_REPO}/${SERVICE_NAME}:${env.BRANCH_NAME_CLEAN}" + '''

                        # Tag as latest/main if main branch
                        if [ "${env.BRANCH_NAME_CLEAN}" == "main" ]; then
                            docker tag ''' + "${DOCKER_HUB_REPO}/${SERVICE_NAME}:${imageTag} ${DOCKER_HUB_REPO}/${SERVICE_NAME}:latest" + '''
                            docker tag ''' + "${DOCKER_HUB_REPO}/${SERVICE_NAME}:${imageTag} ${DOCKER_HUB_REPO}/${SERVICE_NAME}:main" + '''
                        fi
                    ''' // Đóng khối sh bằng '''
                }
            }
        }

        stage('Push to Docker Hub') {
            steps {
                script {
                    def imageTag = env.COMMIT_ID

                    withCredentials([usernamePassword(credentialsId: 'docker-hub-pat-hytaty', usernameVariable: 'DOCKER_USER', passwordVariable: 'DOCKER_PASS')]) {
                        sh '''
                            echo "Pushing images to Docker Hub..."
                            echo \$DOCKER_PASS | docker login -u \$DOCKER_USER --password-stdin

                            # Push image with commit ID tag
                            docker push ''' + "${DOCKER_HUB_REPO}/${SERVICE_NAME}:${imageTag}" + '''

                            # Push branch tag
                            docker push ''' + "${DOCKER_HUB_REPO}/${SERVICE_NAME}:${env.BRANCH_NAME_CLEAN}" + '''

                            # Push latest/main if main branch
                            if [ "${env.BRANCH_NAME_CLEAN}" == "main" ]; then
                                docker push ''' + "${DOCKER_HUB_REPO}/${SERVICE_NAME}:latest" + '''
                                docker push ''' + "${DOCKER_HUB_REPO}/${SERVICE_NAME}:main" + '''
                            fi

                            echo "Successfully pushed ${SERVICE_NAME}:${imageTag}"
                        '''
                    }
                }
            }
        }
    }

    post {
        always {
            cleanWs()
        }
        success {
            echo "CI Pipeline completed successfully for ${env.SERVICE_NAME}"
        }
        failure {
            echo "CI Pipeline failed for ${env.SERVICE_NAME}"
        }
    }
}