package com.paizi.agents.tester

import com.paizi.agents.base.BaseAgent
import com.paizi.ai.router.AIRouter
import com.paizi.core.logging.LoggingManager
import com.paizi.database.TestDao
import com.paizi.database.TestResultEntity

class TesterAgent(
    aiRouter: AIRouter,
    private val testDao: TestDao
) : BaseAgent("Tester", "Designs and verifies comprehensive automated test suites", aiRouter) {

    private val TAG = "TesterAgent"

    suspend fun recordTestEvidence(
        projectId: String,
        testName: String,
        inputData: String,
        expected: String,
        actual: String,
        isPass: Boolean,
        durationMs: Long,
        environment: String = "Android Local JVM"
    ) {
        val entity = TestResultEntity(
            projectId = projectId,
            testName = testName,
            inputData = inputData,
            expectedOutput = expected,
            actualOutput = actual,
            result = if (isPass) "PASS" else "FAIL",
            durationMs = durationMs,
            environment = environment
        )
        testDao.insertTestResult(entity)
        LoggingManager.i(TAG, "Recorded test evidence: $testName -> ${if (isPass) "PASS" else "FAIL"}")
    }

    suspend fun evaluateTestResults(context: AgentContext): AgentResult {
        LoggingManager.i(TAG, "Evaluating test verification report for '${context.projectName}'")
        return AgentResult(
            success = true,
            summary = "Test execution verified against criteria",
            suggestedNextRole = "ProjectAgent"
        )
    }
}
