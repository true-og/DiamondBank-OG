package net.trueog.diamondbankog

data class CriteriaResult(val criteria: String, val value: String, val passed: Boolean)

data class TestResult(val name: String, val criteriaResults: Array<CriteriaResult>) {
    override fun toString(): String {
        val passed = criteriaResults.all { it.passed }
        return "${if (passed) "<green>" else "<red>"}Test \"$name\" ${if (passed) "passed" else "failed: ${
            criteriaResults.filter { !it.passed }.joinToString(
                ", "
            ) { "criteria \"${it.criteria}\" not met (was \"${it.value}\")" }
        }"}"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as TestResult

        if (name != other.name) return false
        if (!criteriaResults.contentEquals(other.criteriaResults)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + criteriaResults.contentHashCode()
        return result
    }
}
