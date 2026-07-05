import static com.kms.katalon.core.testobject.ObjectRepository.findTestObject

import com.kms.katalon.core.webui.keyword.WebUiBuiltInKeywords as WebUI
import com.kms.katalon.core.annotation.AfterTestCase
import com.kms.katalon.core.context.TestCaseContext
import com.kms.katalon.core.configuration.RunConfiguration
import com.kms.katalon.core.webui.driver.DriverFactory
import groovy.json.JsonOutput
import org.apache.commons.io.FileUtils
import java.util.Base64
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths

class TestListener {
	
		@AfterTestCase
		def afterTestCase(TestCaseContext testCaseContext) {
	
			if (testCaseContext.getTestCaseStatus() != "FAILED") {
				return
			}
	
			String screenshotDir = RunConfiguration.getProjectDir() + "/Screenshots/"
			File screenshotFolder = new File(screenshotDir)
	
			if (!screenshotFolder.exists()) {
				screenshotFolder.mkdirs()
			}
	
			String screenshotName = "failure_" + System.currentTimeMillis() + ".png"
			String fullPath = screenshotDir + screenshotName
			WebUI.takeScreenshot(fullPath)
			String encodedImage = ""
	
			try {
	
				File file = new File(fullPath)
	
				if (file.exists()) {
	
					byte[] fileContent =
							FileUtils.readFileToByteArray(file)
	
					encodedImage =
							Base64.getEncoder()
								  .encodeToString(fileContent)
				}
	
			} catch (Exception e) {
	
				println("Failed to encode screenshot")
				println(e.getMessage())
			}
	
			String currentUrlValue = "Browser not opened"
	
			try {
	
				def driver = DriverFactory.getWebDriver()
	
				if (driver != null) {
					currentUrlValue = driver.getCurrentUrl()
				}
	
			} catch (Exception e) {
	
				currentUrlValue = "Failed to get URL"
			}
	
			String executionLog = ""
			List<String> executedSteps = []
			String failedStep = ""
	
			try {
				String reportFolder =
						RunConfiguration.getReportFolder()
	
				println("Report Folder: " + reportFolder)
	
				File reportDir = new File(reportFolder)
	
				File logFile = reportDir.listFiles()?.find {
					it.name.endsWith(".log")
				}
	
				if (logFile != null) {
					println("Found log file: " + logFile.name)
					
					executionLog = logFile.getText("UTF-8")
					executionLog.readLines().findAll { 
						it.contains("DEBUG") 
					}.take(10).each { println(it) }
					executionLog.readLines().findAll { 
						it =~ /- \d+: /
					}.take(10).each { println(it) }
					executedSteps = executionLog.readLines().findAll {
						it.contains("DEBUG") && it =~ /- \d+: /
					}.collect { line ->
						def matcher = line =~ /- (\d+: .+)$/
						if (matcher.find()) {
							return matcher.group(1).trim()
						}
						return null
					}.findAll { it != null }

					if (!executedSteps.isEmpty()) {
						failedStep = executedSteps.last()
					}

					println("executedSteps count: " + executedSteps.size())
					executedSteps.each { println("  >> " + it) }
	
				} else {
					println("No log file found in report folder")
				}
	
			} catch (Exception e) {
				println("Failed reading execution log")
				println(e.getMessage())
			}
	
			def data = [
					testCaseId        : testCaseContext.getTestCaseId(),
					status            : "FAILED",
					timestamp         : new Date().toString(),
					url               : currentUrlValue,
					message           : testCaseContext.getMessage(),
					failed_step       : failedStep,
					executed_steps    : executedSteps,
					screenshot_path   : fullPath,
					screenshot_base64 : encodedImage
			]
	
			String payload =
				JsonOutput.prettyPrint(
					JsonOutput.toJson(data)
					)
			try {
	            // Ubah Kode ini dengan URL webhook anda
				String targetUrl = "https://[CHANGE_THIS_URL]/webhook/bug-report"
	
				println("Sending webhook to: " + targetUrl)
	
				URL url = new URL(targetUrl)
	
				HttpURLConnection conn =
						(HttpURLConnection) url.openConnection()
	
				conn.setRequestMethod("POST")
				conn.setRequestProperty( "Content-Type", "application/json")
				conn.setRequestProperty( "User-Agent", "Katalon-Automation")
	
				conn.setConnectTimeout(10000)
				conn.setReadTimeout(10000)
				conn.setDoOutput(true)
	
				OutputStream os = conn.getOutputStream()
	
				os.write(payload.getBytes("UTF-8"))
	
				os.flush()
				os.close()
	
				int code = conn.getResponseCode()
	
				println("Response server:")
				println("HTTP Code: " + code)
				println("Message   : " + conn.getResponseMessage())
				try {
					println("Body: " + conn.getInputStream().getText())
				} catch (Exception ex) {
					println("Error Body: " + conn.getErrorStream()?.getText())
				}
				conn.disconnect()
			} catch (Exception e) {
				println("Failed send data to webhook")
				e.printStackTrace()
			}
		}
	}
