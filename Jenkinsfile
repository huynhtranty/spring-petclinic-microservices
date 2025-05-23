pipeline {
    agent any

    environment {
        DOCKERHUB_CREDENTIALS = credentials('docker-hub') // Tạo trong Jenkins
        DOCKERHUB_NAMESPACE = 'springcommunity'
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
                script {
                    COMMIT_ID = sh(script: "git rev-parse --short HEAD", returnStdout: true).trim()
                }
            }
        }

        stage('Build & Push Images') {
            matrix {
                axes {
                    axis {
                        name 'SERVICE'
                        values 'customers-service', 'visits-service', 'vets-service', 'genai-service', 'api-gateway', 'admin-server', 'config-server', 'discovery-server'
                    }
                }
                stages {
                    stage("Build ${SERVICE}") {
                        steps {
                            script {
                                def serviceName = "${SERVICE}"
                                def artifactName = "${SERVICE}" // sẽ dùng cho file JAR: service/target/service.jar
                                def exposedPorts = [
                                    'config-server': 8888,
                                    'discovery-server': 8761,
                                    'customers-service': 8081,
                                    'visits-service': 8082,
                                    'vets-service': 8083,
                                    'genai-service': 8084,
                                    'api-gateway': 8080,
                                    'admin-server': 9090
                                ]
                                def port = exposedPorts.get(serviceName, 8080)

                                // 1. Build JAR
                                sh "mvn -f ${serviceName}/pom.xml clean package -DskipTests"

                                // 2. Docker build
                                sh """
                                    docker build \
                                        -f docker/Dockerfile \
                                        --build-arg ARTIFACT_NAME=${serviceName}/target/${artifactName} \
                                        --build-arg EXPOSED_PORT=${port} \
                                        -t ${DOCKERHUB_NAMESPACE}/${serviceName}:${COMMIT_ID} \
                                        .
                                """

                                // 3. Push
                                withCredentials([usernamePassword(credentialsId: "${DOCKERHUB_CREDENTIALS}", usernameVariable: 'DOCKER_USER', passwordVariable: 'DOCKER_PASS')]) {
                                    sh """
                                        echo "$DOCKER_PASS" | docker login -u "$DOCKER_USER" --password-stdin
                                        docker push ${DOCKERHUB_NAMESPACE}/${serviceName}:${COMMIT_ID}
                                    """
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    post {
        success {
            echo "✅ All services built and pushed with tag ${COMMIT_ID}"
        }
        failure {
            echo "❌ CI failed"
        }
    }
}
