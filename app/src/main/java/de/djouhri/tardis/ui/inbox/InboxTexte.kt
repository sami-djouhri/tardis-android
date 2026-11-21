package de.djouhri.tardis.ui.inbox

import de.djouhri.tardis.data.model.tardis.InboxItem
import de.djouhri.tardis.data.model.tardis.InboxLink

/** Thema der Nachrichten-Engine in Worten; Unbekanntes bleibt wie geliefert. */
fun themaText(thema: String?): String? = when (thema) {
    null, "" -> null
    "security_vuln" -> "Sicherheitslücke"
    "security_attack" -> "Angriff"
    "tech_infra" -> "Technik"
    "ai_ml" -> "KI"
    "general" -> "Allgemein"
    "politik" -> "Politik"
    else -> thema
}

/** „Sicherheitslücke · 3 Quellen: Heise, Golem, ZEIT“ oder null ohne Angaben. */
fun quellenZeile(item: InboxItem): String? {
    val namen = item.links.mapNotNull { it.quelle?.takeIf(String::isNotBlank) }.distinct()
    val anzahl = maxOf(item.sourceCount ?: 0, namen.size)
    val quellen = when {
        namen.isEmpty() -> null
        anzahl <= 1 -> namen.first()
        else -> "$anzahl Quellen: ${namen.take(3).joinToString(", ")}" + if (namen.size > 3) " …" else ""
    }
    return listOfNotNull(themaText(item.thema), quellen).joinToString(" · ").ifBlank { null }
}

/** Ein Treffer im Haus ist eine Warnung wert, „kein Treffer“ nicht. */
fun bezugTrifft(bezug: String?): Boolean =
    bezug != null && bezug.startsWith("Im Haus: ") &&
        !bezug.startsWith("Im Haus: kein Treffer") &&
        !bezug.startsWith("Im Haus: keine bekannte") &&
        !bezug.startsWith("Im Haus: nicht geprüft")

/** Nur http(s)-Links taugen fürs Öffnen; Dubletten derselben Adresse raus. */
fun oeffenbareLinks(item: InboxItem): List<InboxLink> {
    val alle = item.links.ifEmpty {
        item.actionUrl?.let { listOf(InboxLink(quelle = null, url = it)) } ?: emptyList()
    }
    return alle.filter { it.url.startsWith("https://") || it.url.startsWith("http://") }.distinctBy { it.url }
}
