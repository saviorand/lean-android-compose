package com.leanandroid.compose

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import org.json.JSONObject

/**
 * Renders a view tree authored in Lean.
 *
 * The tree arrives as JSON from [Lean.screenJson]. Lean's type checker has already
 * ruled out the invalid nestings (a button where a top bar belongs, say), so this
 * side only has to map node kinds onto Composables.
 */
@Composable
fun LeanView(node: JSONObject, onAction: (String) -> Unit) {
    when (node.getString("t")) {
        "scaffold" -> {
            val bar = node.optJSONObject("bar")
            Scaffold(
                topBar = {
                    if (bar != null) LeanView(bar, onAction)
                }
            ) { padding ->
                Box(Modifier.padding(padding)) {
                    LeanView(node.getJSONObject("body"), onAction)
                }
            }
        }

        "topAppBar" -> @OptIn(ExperimentalMaterial3Api::class) TopAppBar(
            title = { Text(node.getString("title")) }
        )

        "column" -> Column(node.modifier()) {
            node.children().forEach { LeanView(it, onAction) }
        }

        "row" -> Row(node.modifier()) {
            node.children().forEach { LeanView(it, onAction) }
        }

        "text" -> Text(
            text = node.getString("content"),
            style = when (node.getString("style")) {
                "titleMedium" -> MaterialTheme.typography.titleMedium
                "bodySmall" -> MaterialTheme.typography.bodySmall
                else -> MaterialTheme.typography.bodyMedium
            }
        )

        "button" -> Button(onClick = { onAction(node.getString("action")) }) {
            Text(node.getString("label"))
        }

        "spacer" -> Spacer(Modifier.size(node.getInt("size").dp))

        // An unknown node means Lean and Kotlin have drifted apart; say so on screen
        // rather than rendering nothing and leaving a blank region to explain.
        else -> Text("unhandled node: ${node.getString("t")}")
    }
}

private fun JSONObject.children(): List<JSONObject> {
    val arr: JSONArray = optJSONArray("children") ?: return emptyList()
    return (0 until arr.length()).map { arr.getJSONObject(it) }
}

private fun JSONObject.modifier(): Modifier {
    val m = optJSONObject("mod") ?: return Modifier
    var mod: Modifier = Modifier
    when (val w = m.opt("width")) {
        "fill" -> mod = mod.fillMaxWidth()
        is Int -> mod = mod.width(w.dp)
    }
    when (val h = m.opt("height")) {
        "fill" -> mod = mod.fillMaxHeight()
        is Int -> mod = mod.height(h.dp)
    }
    val padding = m.optInt("padding", 0)
    if (padding > 0) mod = mod.padding(padding.dp)
    return mod
}

/**
 * A screen whose structure Lean computes on the device.
 *
 * [count] is passed into Lean, which returns a whole view tree for that state. The
 * layout is therefore a function of runtime state rather than something fixed at
 * build time, which is what a server-driven UI needs: swap the Lean side and the
 * screen changes without touching this file.
 */
@Composable
fun LeanAuthoredScreen(count: Int, onAction: (String) -> Unit) {
    val tree = androidx.compose.runtime.remember(count) {
        runCatching { JSONObject(Lean.screenJson(count)) }.getOrNull()
    }
    if (tree == null) {
        Text("Lean did not return a renderable tree")
    } else {
        LeanView(tree, onAction)
    }
}
