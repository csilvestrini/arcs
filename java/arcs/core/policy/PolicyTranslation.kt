package arcs.core.policy

import arcs.core.analysis.RecipeGraph
import arcs.core.data.AccessPath
import arcs.core.data.Check
import arcs.core.data.Claim
import arcs.core.data.HandleConnectionSpec
import arcs.core.data.InformationFlowLabel.Predicate
import arcs.core.data.InformationFlowLabel.SemanticTag
import arcs.core.data.Recipe
import arcs.core.host.toSchema

/**
 * Translates the given [Policy] into dataflow analysis checks and claims on the given
 * [RecipeGraph], returning a modified copy of the graph.
 */
fun applyPolicy(policy: Policy, graph: RecipeGraph): RecipeGraph {
    val egressParticleNodes = graph.particleNodes.filterNot { it.particle.spec.isolated }
    checkEgressParticles(policy, egressParticleNodes)

    // Add check statements to every egress particle node.
    val checkPredicate = createCheckPredicate(policy)
    val additionalChecks = egressParticleNodes.associateWith { node ->
        // Each handle connection needs its own check statement.
        node.particle.spec.connections.values
            .filter { it.direction.canRead }
            .flatMap { connectionSpec ->
                createChecks(node.particle, connectionSpec, checkPredicate)
            }
    }

    // TODO(b/157605232): Add additional claims.
    val additionalClaims = emptyMap<RecipeGraph.Node.Particle, List<Claim>>()

    return graph.copyWith(additionalClaims, additionalChecks)
}

/**
 * Creates [Check]s for the given [particle]'s [connectionSpec]. The given [checkPredicate] is used
 * to create a new check statement targeting every field in the connection's type (including deeply
 * nested fields).
 */
private fun createChecks(
    particle: Recipe.Particle,
    connectionSpec: HandleConnectionSpec,
    checkPredicate: Predicate
): List<Check> {
    val schema = connectionSpec.type.toSchema()
    val checks = mutableListOf<Check.Assert>()
    // Add a check for the top-level type.
    checks.add(Check.Assert(AccessPath(particle, connectionSpec), checkPredicate))
    // Add a check for each field (including nested fields).
    schema.visitFields { selectors, _ ->
        checks.add(Check.Assert(AccessPath(particle, connectionSpec, selectors), checkPredicate))
    }
    return checks
}

/**
 * Verifies that the given egress particle nodes match the policy. The only egress particle allowed
 * to be used with a policy named `Foo` is an egress particle named `Egress_Foo`.
 */
private fun checkEgressParticles(
    policy: Policy,
    egressParticleNodes: List<RecipeGraph.Node.Particle>
) {
    val invalidEgressParticles = egressParticleNodes
        .map { it.particle.spec }
        .filter { it.name != policy.egressParticleName }
    if (invalidEgressParticles.isNotEmpty()) {
        throw PolicyViolation.InvalidEgressParticle(policy, invalidEgressParticles.map { it.name })
    }
}

/**
 * Constructs the [Predicate] that is to be used for checks on egress particles in the given
 * [Policy].
 *
 * For policies which don't require any redaction labels, the check is a simple one of the form
 * `check x is allowedForEgress`.
 *
 * For policies with redaction labels (e.g. `label1`, `label2`, `label3`), the check looks so:
 * ```
 * check x is allowedForEgress
 *   or (is allowedForEgress_label1 and is label1)
 *   or (is allowedForEgress_label2 and is label2)
 *   or (is allowedForEgress_label3 and is label3)
 * ```
 */
private fun createCheckPredicate(policy: Policy): Predicate {
    val allowedForEgress = labelPredicate("allowedForEgress")
    if (policy.allRedactionLabels.isEmpty()) {
        return allowedForEgress
    }
    // List of predicates of the form: allowedForEgress_X AND X.
    val labelPredicates = policy.allRedactionLabels.sorted().map { label ->
        labelPredicate("allowedForEgress_$label") and labelPredicate(label)
    }
    // OR the predicates for each redaction label together.
    return Predicate.or(listOf(allowedForEgress) + labelPredicates)
}

/** Returns a copy of the [RecipeGraph] with extra checks and claims added to particle nodes. */
private fun RecipeGraph.copyWith(
    additionalClaims: Map<RecipeGraph.Node.Particle, List<Claim>>,
    additionalChecks: Map<RecipeGraph.Node.Particle, List<Check>>
): RecipeGraph {
    return RecipeGraph(
        particleNodes.map { node ->
            RecipeGraph.Node.Particle(
                node.particle,
                node.claims + additionalClaims.getOrDefault(node, emptyList()),
                node.checks + additionalChecks.getOrDefault(node, emptyList())
            )
        },
        handleNodes
    )
}

private fun labelPredicate(label: String) = Predicate.Label(SemanticTag(label))

/** Indicates that a policy was violated by a recipe. */
sealed class PolicyViolation(val policy: Policy, message: String) : Exception(
    "Policy ${policy.name} violated: $message"
) {
    /** Thrown when egress particles were found in the recipe that are not allowed by policy. */
    class InvalidEgressParticle(
        policy: Policy,
        val particleNames: List<String>
    ) : PolicyViolation(
        policy,
        "Egress particle allowed by policy is ${policy.egressParticleName} but found: " +
            particleNames.joinToString()
    )
}
