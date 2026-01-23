package net.trueog.diamondbankog

interface Test {
    val name: String

    fun runTests(): Array<TestResult>
}
