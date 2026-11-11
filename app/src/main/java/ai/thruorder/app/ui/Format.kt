package ai.thruorder.app.ui

import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val wonFormat = NumberFormat.getNumberInstance(Locale.KOREA)
private val hm = SimpleDateFormat("HH:mm", Locale.KOREA)

fun Int.won(): String = wonFormat.format(this) + "원"
fun Long.hhmm(): String = hm.format(Date(this))
fun Int.minutesLabel(): String = "${(this + 59) / 60}분"
