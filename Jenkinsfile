import groovy.json.JsonOutput // Import JsonOutput

pipeline {
    agent any

    environment {
        DOCKER_HUB_REPO = 'hytaty'
        DOCKERFILE_PATH = 'docker/Dockerfile'
        PROJECT_VERSION = '3.4.1' // ĐẢM BẢO GIÁ TRỊ NÀY KHỚP VỚI <version> TRONG pom.xml GỐC CỦA BẠN
    }

    stages {
        stage('Checkout') {
            steps {
                script {
                    checkout scm

                    // === KHẮC PHỤC LỖI: Chuyển đổi Map thành JSON string một cách tường minh ===
                    // Định nghĩa Map
                    def servicePortsMap = [
                        'spring-petclinic-admin-server': '9090',
                        'spring-petclinic-api-gateway': '8080',
                        'spring-petclinic-config-server': '8888',
                        'spring-petclinic-customers-service': '8081',
                        'spring-petclinic-discovery-server': '8761',
                        'spring-petclinic-genai-service': '8084',
                        'spring-petclinic-vets-service': '8083',
                        'spring-petclinic-visits-service': '8082'
                    ]
                    // Chuyển đổi Map thành JSON string và lưu vào env
                    env.SERVICE_PORTS_JSON = JsonOutput.toJson(servicePortsMap)

                    env.COMMIT_ID = sh(returnStdout: true, script: 'git rev-parse --short HEAD').trim()
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
                        ./mvnw clean install -DskipTests
                    '''
                    echo "All microservices JARs built successfully."
                }
            }
        }

        stage('Build and Push Docker Images') {
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

                    def servicesFound = findFiles(glob: '*/pom.xml')
                    def projectServiceNames = servicesFound.collect { fileWrapper ->
                        return fileWrapper.path.split('/')[0]
                    }.unique()

                    def servicesToProcess = projectServiceNames.findAll { serviceName ->
                        VALID_SERVICES.contains(serviceName)
                    }

                    withCredentials([usernamePassword(credentialsId: 'docker-hub', usernameVariable: 'DOCKER_USER', passwordVariable: 'DOCKER_PASS')]) {
                        sh "echo \$DOCKER_PASS | docker login -u \$DOCKER_USER --password-stdin"
                        echo "Logged into Docker Hub."

                        // === KHẮC PHỤC LỖI: Đọc lại JSON string thành Map ===
                        def servicePorts = readJSON text: env.SERVICE_PORTS_JSON

                        for (def serviceName : servicesToProcess) {
                            def artifactName = "${serviceName}-${env.PROJECT_VERSION}"
                            
                            def exposedPort = servicePorts[serviceName]
                            if (!exposedPort) {
                                error "Error: Port not defined for service ${serviceName} in SERVICE_PORTS_JSON. Please update Jenkinsfile."
                            }

                            def targetImageRepo = "${env.DOCKER_HUB_REPO}/${serviceName.toLowerCase()}"
                            def commitIdTag = env.COMMIT_ID

                            echo "--- Processing image for ${serviceName} ---"
                            echo "Building image: ${targetImageRepo}:${commitIdTag}"
                            echo "Using Dockerfile: ${env.DOCKERFILE_PATH}"
                            echo "ARTIFACT_NAME: ${artifactName}, EXPOSED_PORT: ${exposedPort}"

                            sh "docker build -t ${targetImageRepo}:${commitIdTag} " +
                               "-f ${env.DOCKERFILE_PATH} " +
                               "--build-arg ARTIFACT_NAME=${artifactName} " +
                               "--build-arg EXPOSED_PORT=${exposedPort} ."
                            
                            echo "Built: ${targetImageRepo}:${commitIdTag}"

                            echo "--- Pushing image to Docker Hub ---"
                            sh "docker push ${targetImageRepo}:${commitIdTag}"
                            echo "Pushed: ${targetImageRepo}:${commitIdTag}"

                            if ("${env.BRANCH_NAME_CLEAN}" == "main") {
                                echo "Branch is 'main', pushing 'latest' and 'main' tags as well."

                                sh "docker tag ${targetImageRepo}:${commitIdTag} ${targetImageRepo}:latest"
                                sh "docker push ${targetImageRepo}:latest"
                                echo "Pushed: ${targetImageRepo}:latest"

                                if (commitIdTag != "main") {
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