package net.trueog.diamondbankog

interface Test {
    val name: String

    suspend fun runTests(): Array<TestResult>
}
