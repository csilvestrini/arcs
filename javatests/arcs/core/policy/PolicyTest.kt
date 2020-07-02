package arcs.core.policy

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class PolicyTest {
    @Test
    fun allFields() {
        val child = PolicyField("child")
        val parent = PolicyField("parent", subfields = listOf(child))
        val other = PolicyField("other")
        val policy = Policy(
            name = "MyPolicy",
            targets = listOf(
                PolicyTarget("target1", fields = listOf(parent)),
                PolicyTarget("target2", fields = listOf(other))
            ),
            egressType = EgressType.LOGGING
        )

        assertThat(policy.allFields).containsExactly(child, parent, other)
    }

    @Test
    fun allRedactionLabels() {
        val child = PolicyField(
            fieldName = "child",
            redactedUsages = mapOf(
                "label1" to setOf(UsageType.EGRESS),
                "label2" to setOf(UsageType.JOIN)
            )
        )
        val parent = PolicyField(
            fieldName = "parent",
            subfields = listOf(child),
            redactedUsages = mapOf(
                "label2" to setOf(UsageType.EGRESS),
                "label3" to setOf(UsageType.EGRESS)
            )
        )
        val other = PolicyField(
            fieldName = "other",
            redactedUsages = mapOf("label4" to setOf(UsageType.EGRESS))
        )
        val policy = Policy(
            name = "MyPolicy",
            targets = listOf(
                PolicyTarget("target1", fields = listOf(parent)),
                PolicyTarget("target2", fields = listOf(other))
            ),
            egressType = EgressType.LOGGING
        )

        assertThat(policy.allRedactionLabels).containsExactly(
            "label1",
            "label2",
            "label3",
            "label4"
        )
    }
}
