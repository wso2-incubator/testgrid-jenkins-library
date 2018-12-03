/*
 * Copyright (c) 2018, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */


import org.wso2.tg.jenkins.Logger
import org.wso2.tg.jenkins.PipelineContext
import org.wso2.tg.jenkins.Properties

// The pipeline should reside in a call block
def call() {
    // Setting the current pipeline context, this should be done initially
    PipelineContext.instance.setContext(this)
    // Initializing environment properties
    def props = Properties.instance
    props.instance.initProperties()
    def log = new Logger()

    final def GIT_REPOSITORY = "${repoName}"
    final def GIT_SSH_URL = "${sshUrl}"
    final def GIT_BRANCH = "${branch}"
    final def TG_YAML_SEARCH_REGEX = "*.-testgrid.yaml"


    pipeline {
        agent {
            node {
                label ""
                customWorkspace "${props.WORKSPACE}"
            }
        }
        // This trigger is removed from the pipeline itself to due to few issues in the plugin when using
        // shared libraries
//        triggers {
//            GenericTrigger(
//                    genericVariables: [
//                            [expressionType: 'JSONPath', key: 'sshUrl', value: '$.repository.ssh_url'],
//                            [expressionType: 'JSONPath', key: 'repoName', value: '$.repository.name'],
//                            [expressionType: 'JSONPath', key: 'branch', value: '$.ref', regexpFilter: 'refs/heads/']
//                    ],
//                    regexpFilterText: '',
//                    regexpFilterExpression: ''
//            )
//        }
        tools {
            jdk 'jdk8'
        }

        stages {
            stage('Receive web Hooks') {
                steps {
                    script {
                        // TODO:  we need to validate the payloads.
                        echo "Recieved the web hook request!"
                        log.info("The git repo name : " + GIT_REPOSITORY)
                        log.info("Git SSH URL : " + GIT_BRANCH)
                        log.info("Git branch : " + GIT_SSH_URL)

                        deleteDir()
                        cloneRepo(GIT_SSH_URL, GIT_BRANCH)
                        findTestGridYamls(pwd() + "/" + GIT_REPOSITORY)
                        // We need to get a list of Jobs that are configured
                        printAllJobs()
                    }
                }
            }
        }
    }
}

void cloneRepo(def gitURL, gitBranch) {
//    sshagent (credentials: ['github_bot']) {
        sh """
            echo Cloning repository: ${gitURL}
            git clone -b ${gitBranch} ${gitURL}
        """
//    }
}

void findTestGridYamls(def searchPath) {
    echo "Searching for TG yamls at : ${searchPath}"
    new File(searchPath).eachFileRecurse() {
        file -> println file.getAbsolutePath()
    }
}

void printAllJobs() {
    Jenkins.instance.getAllItems(AbstractItem.class).each {
        echo "${it.fullName}"
    }
}


