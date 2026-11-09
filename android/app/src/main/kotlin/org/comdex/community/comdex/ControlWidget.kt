package org.comdex.community.comdex

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.view.View
import android.widget.RemoteViews

open class ControlWidget : AppWidgetProvider() {
    protected open val layout = R.layout.control_widget

    companion object {
        private const val COMMAND = "org.comdex.community.COMMAND"

        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            for (provider in listOf(ControlWidget(), VerticalWidget())) {
                val ids = manager.getAppWidgetIds(ComponentName(context, provider.javaClass))
                if (ids.isNotEmpty()) provider.onUpdate(context, manager, ids)
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action != COMMAND) return
        val command = intent.getStringExtra("command") ?: return
        val service = BleService.instance
        if (service?.widgetConnected() != true) {
            context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } else {
            service.widgetCommand("comdex", command)
        }
    }

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val view = RemoteViews(context.packageName, layout)
        val service = BleService.instance?.takeIf { it.widgetConnected() }
        val battery = service?.widgetBattery("comdex")
        view.setTextViewText(R.id.widget_battery, battery?.takeIf { it in 0..100 }?.let { "$it%" } ?: context.getString(R.string.widget_charge_unknown))
        val color = when (DeviceRules.batteryWarning(battery)) {
            2 -> Color.rgb(255, 133, 133)
            1 -> Color.rgb(255, 200, 100)
            else -> Color.rgb(133, 227, 204)
        }
        view.setTextColor(R.id.widget_battery, color)
        val roomOff = service?.widgetRoomOff() == true
        view.setImageViewResource(R.id.widget_room_icon,
            if (roomOff) R.drawable.widget_waves_off else R.drawable.widget_waves)
        view.setContentDescription(R.id.widget_room,
            context.getString(if (roomOff) R.string.widget_room_off else R.string.widget_room_on))
        view.setViewVisibility(R.id.widget_controls, if (service == null) View.GONE else View.VISIBLE)
        view.setViewVisibility(R.id.widget_offline, if (service == null) View.VISIBLE else View.GONE)
        val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        view.setOnClickPendingIntent(R.id.widget_offline, open)
        view.setOnClickPendingIntent(R.id.widget_open, open)
        val commands = listOf(R.id.widget_down to "down", R.id.widget_up to "up", R.id.widget_room to "room")
        commands.forEach { (button, command) ->
            val intent = Intent(context, javaClass).setAction(COMMAND).putExtra("command", command)
            val pending = PendingIntent.getBroadcast(context, button, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            view.setOnClickPendingIntent(button, if (service == null) open else pending)
        }
        manager.updateAppWidget(ids, view)
    }
}

class VerticalWidget : ControlWidget() {
    override val layout = R.layout.vertical_widget
}
