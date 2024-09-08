package com.hypermagik.spectrum

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import java.util.function.Function

class Permissions {
    companion object {
        private const val ACTION = "com.hypermagik.spectrum.USB_PERMISSION"

        fun request(context: Context, usbManager: UsbManager, usbDevice: UsbDevice, callback: Function<String?, Unit>) {
            if (usbManager.hasPermission(usbDevice)) {
                callback.apply(null)
            } else {
                registerNewBroadcastReceiver(context, usbDevice, callback)

                var flags = 0
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    flags = PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_ALLOW_UNSAFE_IMPLICIT_INTENT
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    flags = PendingIntent.FLAG_MUTABLE
                }

                usbManager.requestPermission(usbDevice, PendingIntent.getBroadcast(context, 0, Intent(ACTION), flags))
            }
        }

        @Suppress("DEPRECATION")
        @SuppressLint("UnspecifiedRegisterReceiverFlag")
        private fun registerNewBroadcastReceiver(context: Context, usbDevice: UsbDevice, callback: Function<String?, Unit>) {
            val broadcastReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    val action = intent.action

                    if (ACTION == action) {
                        synchronized(this) {
                            val manager = context.getSystemService(Context.USB_SERVICE) as UsbManager
                            val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                            } else {
                                intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                            }
                            if (usbDevice == device) {
                                if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                                    if (manager.hasPermission(device)) {
                                        callback.apply(null)
                                    } else {
                                        callback.apply("Permission was granted but cannot access the device")
                                    }
                                } else {
                                    callback.apply("Permission was not granted")
                                }
                            } else {
                                callback.apply("Got permission for an unexpected device")
                            }
                        }
                    } else {
                        callback.apply("Unexpected action received")
                    }

                    context.unregisterReceiver(this)
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(broadcastReceiver, IntentFilter(ACTION), Context.RECEIVER_EXPORTED)
            } else {
                context.registerReceiver(broadcastReceiver, IntentFilter(ACTION))
            }
        }
    }
}