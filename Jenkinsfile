pipeline {
    agent any

    parameters {
        string(name: 'admin-server', defaultValue: 'main', description: 'Branch to build for admin-server')
        string(name: 'api-gateway', defaultValue: 'main', description: 'Branch to build for api-gateway')
        string(name: 'config-server', defaultValue: 'main', description: 'Branch to build for config-server')
        string(name: 'customer-service', defaultValue: 'main', description: 'Branch to build for customer-service')
        string(name: 'discovery-server', defaultValue: 'main', description: 'Branch to build for discovery-server')
        string(name: 'genai-service', defaultValue: 'main', description: 'Branch to build for generic-service')
        string(name: 'vets-service', defaultValue: 'main', description: 'Branch to build for vets-service')
        string(name: 'visits-service', defaultValue: 'main', description: 'Branch to build for visit-service')
    }

    environment {
        DOCKER_HUB_CREDS = credentials('docker-hub')
        REPOSITORY_PREFIX = "hytaty"
    }

    stages {
        stage('Initialize') {
            steps {
                script {
                    services = [
                        'spring-petclinic-admin-server'     : params['admin-server'],
                        'spring-petclinic-api-gateway'      : params['api-gateway'],
                        'spring-petclinic-config-server'    : params['config-server'],
                        'spring-petclinic-customers-service': params['customer-service'],
                        'spring-petclinic-discovery-server' : params['discovery-server'],
                        'spring-petclinic-genai-service'    : params['genai-service'],
                        'spring-petclinic-vets-service'     : params['vets-service'],
                        'spring-petclinic-visits-service'   : params['visits-service']
                    ]
                    COMMIT_ID = sh(script: 'git rev-parse --short HEAD', returnStdout: true).trim()
                }
            }
        }

        stage('Checkout') {
            steps {
                checkout scm
                script {
                    services.each { service, branch ->
                        if (branch != 'main') {
                            echo "Using branch ${branch} for ${service}"
                            sh "git checkout ${branch} -- ${service} || echo 'Failed to checkout ${branch} for ${service}'"
                        }
                    }
                }
            }
        }

        stage('Build Images') {
            steps {
                script {
                    services.each { service, branch ->
                        echo "Building Docker image for ${service} on branch ${branch}"

                        sh """
                            cd ${service}
                            ../mvnw clean install -P buildDocker -Ddocker.image.prefix=${REPOSITORY_PREFIX} -DskipTests
                        """

                        def tag = (branch == 'main') ? 'latest' : COMMIT_ID

                        sh """
                            docker tag ${REPOSITORY_PREFIX}/${service} ${REPOSITORY_PREFIX}/${service}:${tag}
                            echo "Tagged ${service} with ${tag}"
                        """

                        def imageExists = sh(script: "docker images -q ${REPOSITORY_PREFIX}/${service}:${tag}", returnStatus: true) == 0
                        if (!imageExists) {
                            error "Docker image for ${service} was not built successfully."
                        }
                    }
                }
            }
        }

        stage('Push Images') {
            steps {
                script {
                    sh """
                        echo "${DOCKER_HUB_CREDS_PSW}" | docker login -u "${DOCKER_HUB_CREDS_USR}" --password-stdin
                        echo "Logged in to Docker Hub"
                    """

                    services.each { service, branch ->
                        def tag = (branch == 'main') ? 'latest' : COMMIT_ID
                        echo "Pushing Docker image for ${service} with tag ${tag}"

                        sh """
                            docker push ${REPOSITORY_PREFIX}/${service}:${tag}
                            echo "Pushed ${service} with tag ${tag}"
                        """
                    }
                }
            }
        }
    }

    post {
        success {
            echo "Pipeline completed successfully!"
        }
        failure {
            echo "Pipeline failed!"
        }
    }
}