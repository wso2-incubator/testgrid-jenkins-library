import groovy.json.*
import java.text.SimpleDateFormat

node('COMPONENT_ECS') {
    def MYSQL_DRIVER_LOCATION='http://central.maven.org/maven2/mysql/mysql-connector-java/6.0.6/mysql-connector-java-6.0.6.jar'
    stage('Deploy to Dev'){
        deleteDir()
        copyArtifacts(projectName: 'testgrid/testgrid', filter: 'distribution/target/*.zip, web/target/*.war, deployment-tinkerer/target/*.war');
        sshagent(['testgrid-dev-key']) {
            sh """
                scp -o StrictHostKeyChecking=no web/target/*.war ${DEV_USER}@${DEV_HOST}:/testgrid/deployment/apache-tomcat-8.5.23/webapps/ROOT.war
                scp -o StrictHostKeyChecking=no deployment-tinkerer/target/*.war ${DEV_USER}@${DEV_HOST}:/testgrid/deployment/apache-tomcat-8.5.23/webapps/
                scp -o StrictHostKeyChecking=no distribution/target/*.zip ${DEV_USER}@${DEV_HOST}:/testgrid/testgrid-home/testgrid-dist/WSO2-TestGrid.zip
                ssh -o StrictHostKeyChecking=no ${DEV_USER}@${DEV_HOST} /bin/bash << EOF
                    cd /testgrid/testgrid-home/testgrid-dist/;
                    unzip WSO2-TestGrid.zip -d WSO2-TestGrid-unzipped;
                    curl -o ./WSO2-TestGrid-unzipped/WSO2-TestGrid/lib/mysql.jar ${MYSQL_DRIVER_LOCATION}
                    rm -rf WSO2-TestGrid-backup;
                    mv WSO2-TestGrid WSO2-TestGrid-backup;
                    mv WSO2-TestGrid-unzipped/WSO2-TestGrid WSO2-TestGrid;
                    ls
                """
           }
    }
    def test_result = "FAIL"
    stage ('Test Dev Deployment'){
        def response1 = sh (returnStdout: true, script: "curl -X GET ${env.TEST_BUILD_URL}${env.TEST_TOKEN} --user ${env.TG_USER}:${env.TG_USER_PASS}")
        echo "Response1: " + response1
        def jobResult = null
        while(jobResult == null) {
            sleep 3
            def response = sh (returnStdout: true, script: "curl --silent -X GET ${env.TEST_STATE_URL} --user ${env.TG_USER}:${env.TG_USER_PASS}")
            def json = new JsonSlurper().parseText(response)
            jobResult = json.result
        } // end while        
         println("Phase1 test job completed with status: " + jobResult)
         test_result = jobResult;
    }
    stage('Deploy to Prod'){
        if(test_result == "SUCCESS") {
            def userInput
            mail (
                to: "${TEAM_MEMBERS},builder@wso2.org",
                subject: "Job '${env.JOB_NAME}' (${env.BUILD_NUMBER}) is waiting for input",
                body: "Hi folks,\nLatest TG distribution is deployed to TestGrid-DEV." +
                    "\n Please check and verify if the existing slaves received the latest pack." +
                    "\nPlease do a round of tests and visit ${env.BUILD_URL} to approve/decline deploying to TestGrid-PROD." +
                    "\nYou have 1 hour left!\n\nThanks!");
            try {
                timeout(time: 1, unit: 'HOURS') {
                    userInput = input (
                            message: 'Deploy to Prod environment?', parameters: [
                            [$class: 'BooleanParameterDefinition', defaultValue: true, description: '', name: 'Please confirm you agree with this.']
                    ])
                }
            } catch(err) { // input false
                userInput = false
                def user = err.getCauses()[0].getUser()
                echo ${user.toString}
                if('SYSTEM' == user.toString()) { // SYSTEM means timeout.
                    echo "Aborted due to time-out."
                } else {
                    echo "Aborted by: [${user}]"
                }
            }
            if (userInput == true) {
                sshagent(['testgrid-prod-key']) {
                    sh """
                        scp -o StrictHostKeyChecking=no web/target/*.war ${PROD_USER}@${PROD_HOST}:/testgrid/deployment/apache-tomcat-8.5.24/webapps/ROOT.war
                        scp -o StrictHostKeyChecking=no deployment-tinkerer/target/*.war ${PROD_USER}@${PROD_HOST}:/testgrid/deployment/apache-tomcat-8.5.24/webapps/
                        scp -o StrictHostKeyChecking=no distribution/target/*.zip ${PROD_USER}@${PROD_HOST}:/testgrid/testgrid-home/testgrid-dist/WSO2-TestGrid.zip
                        ssh -o StrictHostKeyChecking=no ${PROD_USER}@${PROD_HOST} /bin/bash << EOF
                            cd /testgrid/testgrid-home/testgrid-dist/;
                            unzip WSO2-TestGrid.zip -d WSO2-TestGrid-unzipped;
                            curl -o ./WSO2-TestGrid-unzipped/WSO2-TestGrid/lib/mysql.jar ${MYSQL_DRIVER_LOCATION}
                            rm -rf WSO2-TestGrid-backup;
                            mv WSO2-TestGrid WSO2-TestGrid-backup;
                            mv WSO2-TestGrid-unzipped/WSO2-TestGrid WSO2-TestGrid;
                            ls
                     """
                }
            } else {
                echo "Not proceeding with prod deployment."
            }
        } else {
            echo 'Tests have failed. Please fix the tests in order for prod deployment'
        }
        
    }

}

