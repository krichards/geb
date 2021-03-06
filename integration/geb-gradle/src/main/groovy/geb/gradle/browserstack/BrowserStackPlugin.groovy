/*
 * Copyright 2014 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package geb.gradle.browserstack

import geb.gradle.browserstack.task.DownloadBrowserStackTunnel
import geb.gradle.cloud.BrowserSpec
import geb.gradle.cloud.task.StartExternalTunnel
import geb.gradle.cloud.task.StopExternalTunnel
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.Sync
import org.gradle.api.tasks.testing.Test

class BrowserStackPlugin implements Plugin<Project> {

    public static final String CLOSE_TUNNEL_TASK_NAME = 'closeBrowserStackTunnel'
    public static final String OPEN_TUNNEL_IN_BACKGROUND_TASK_NAME = 'openBrowserStackTunnelInBackground'
    public static final String UNZIP_TUNNEL_TASK_NAME = 'unzipBrowserStackTunnel'
    Project project

    @Override
    void apply(Project project) {
        this.project = project

        def browserStackExtension = project.extensions.create('browserStack', BrowserStackExtension, project)
        browserStackExtension.addExtensions()

        addTunnelTasks(browserStackExtension)
        addBrowserStackTasks()
    }

    void addBrowserStackTasks() {
        def allBrowserStackTests = project.task("allBrowserStackTests") {
            group "BrowserStack Test"
        }

        project.browserStack.browsers.all { BrowserSpec browser ->
            def testTask = project.task("${browser.displayName}Test", type: Test) { Test task ->
                group allBrowserStackTests.group
                task.dependsOn OPEN_TUNNEL_IN_BACKGROUND_TASK_NAME
                allBrowserStackTests.dependsOn task
                finalizedBy CLOSE_TUNNEL_TASK_NAME

                systemProperty 'geb.build.reportsDir', project.reporting.file("$name-geb")

                browser.testTask = task
                browser.configureTestTask()
            }

            def decorateReportsTask = project.task("${browser.displayName}DecorateReports", type: Copy) {
                from testTask.reports.junitXml.destination
                into "${testTask.reports.junitXml.destination}-decorated"
                filter { it.replaceAll("(testsuite|testcase) name=\"(.+?)\"", "\$1 name=\"\$2 ($browser.displayName)\"") }
            }

            testTask.finalizedBy decorateReportsTask
        }
    }

    void addTunnelTasks(BrowserStackExtension browserStackExtension) {
        def downloadBrowserStackTunnel = project.task('downloadBrowserStackTunnel', type: DownloadBrowserStackTunnel)

        def unzipBrowserStackTunnel = project.task(UNZIP_TUNNEL_TASK_NAME, type: Sync) {
            dependsOn downloadBrowserStackTunnel

            from(project.zipTree(downloadBrowserStackTunnel.outputs.files.singleFile))
            into(project.file("${project.buildDir}/browserstack/unzipped"))
        }

        def closeBrowserStackTunnel = project.task(CLOSE_TUNNEL_TASK_NAME, type: StopExternalTunnel) {
            tunnel = project.browserStack.tunnel
        }

        def openBrowserStackTunnel = project.task('openBrowserStackTunnel', type: StartExternalTunnel)

        def openBrowserStackTunnelInBackground = project.task(OPEN_TUNNEL_IN_BACKGROUND_TASK_NAME, type: StartExternalTunnel) {
            inBackground = true
            finalizedBy CLOSE_TUNNEL_TASK_NAME
        }

        [openBrowserStackTunnel, openBrowserStackTunnelInBackground].each {
            it.configure {
                dependsOn UNZIP_TUNNEL_TASK_NAME

                tunnel = project.browserStack.tunnel
                workingDir = project.buildDir
            }
        }

        [downloadBrowserStackTunnel, unzipBrowserStackTunnel, openBrowserStackTunnelInBackground, closeBrowserStackTunnel].each {
            it.configure {
                onlyIf { browserStackExtension.useTunnel }
            }
        }
    }
}
