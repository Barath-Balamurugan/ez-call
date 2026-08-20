package com.ezcall.onetoone

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.telecom.DisconnectCause
import android.util.Log
import androidx.core.telecom.CallAttributesCompat
import androidx.core.telecom.CallsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * Keeps EZ Call's WebRTC calls synchronized with Android Telecom.
 *
 * Signaling and media remain owned by Firebase/WebRTC. Telecom owns the device-level call
 * lifecycle, audio endpoint selection, Bluetooth integration, mute state, and external controls.
 */
object EzCallTelecomManager {
    private const val TAG = "EzCallTelecom"

    interface Listener {
        fun onTelecomAnswerRequested(callId: String)
        fun onTelecomDisconnectRequested(callId: String, disconnectCode: Int)
        fun onTelecomSetActiveRequested(callId: String)
        fun onTelecomSetInactiveRequested(callId: String)
        fun onTelecomMuteChanged(callId: String, muted: Boolean)
    }

    private sealed interface Command {
        data object Answer : Command
        data object SetActive : Command
        data object SetInactive : Command
        data class Disconnect(val cause: DisconnectCause) : Command
    }

    private class Session(
        val callId: String,
        val contactName: String,
        val phoneNumber: String,
        val incoming: Boolean,
        val commands: Channel<Command> = Channel(Channel.UNLIMITED)
    ) {
        @Volatile
        var listener: Listener? = null

        @Volatile
        var registered = false

        @Volatile
        var active = false
    }

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val sessions = ConcurrentHashMap<String, Session>()
    private lateinit var appContext: Context
    private lateinit var callsManager: CallsManager

    @Volatile
    private var initialized = false

    @JvmStatic
    @Synchronized
    fun initialize(context: Context) {
        if (initialized) {
            return
        }
        appContext = context.applicationContext
        callsManager = CallsManager(appContext)
        try {
            callsManager.registerAppWithTelecom(
                CallsManager.CAPABILITY_BASELINE or
                    CallsManager.CAPABILITY_SUPPORTS_VIDEO_CALLING
            )
            initialized = true
        } catch (error: RuntimeException) {
            Log.e(TAG, "Could not register EZ Call with Android Telecom", error)
        }
    }

    @JvmStatic
    fun registerIncoming(
        context: Context,
        callId: String,
        contactName: String,
        phoneNumber: String
    ) {
        register(context, callId, contactName, phoneNumber, true)
    }

    @JvmStatic
    fun registerOutgoing(
        context: Context,
        callId: String,
        contactName: String,
        phoneNumber: String
    ) {
        register(context, callId, contactName, phoneNumber, false)
    }

    @JvmStatic
    fun setListener(callId: String, listener: Listener?) {
        sessions[normalize(callId)]?.listener = listener
    }

    @JvmStatic
    fun removeListener(callId: String, listener: Listener) {
        val session = sessions[normalize(callId)] ?: return
        if (session.listener === listener) {
            session.listener = null
        }
    }

    @JvmStatic
    fun answer(callId: String) {
        send(callId, Command.Answer)
    }

    @JvmStatic
    fun setActive(callId: String) {
        send(callId, Command.SetActive)
    }

    @JvmStatic
    fun setInactive(callId: String) {
        send(callId, Command.SetInactive)
    }

    @JvmStatic
    fun disconnect(callId: String, disconnectCode: Int) {
        send(callId, Command.Disconnect(DisconnectCause(disconnectCode)))
    }

    @JvmStatic
    fun isManaging(callId: String): Boolean = sessions.containsKey(normalize(callId))

    private fun register(
        context: Context,
        callId: String,
        contactName: String,
        phoneNumber: String,
        incoming: Boolean
    ) {
        initialize(context)
        if (!initialized) {
            return
        }
        val normalizedCallId = normalize(callId)
        if (normalizedCallId.isEmpty()) {
            return
        }
        val session = Session(
            normalizedCallId,
            contactName.ifBlank { "EZ Call contact" },
            phoneNumber,
            incoming
        )
        if (sessions.putIfAbsent(normalizedCallId, session) != null) {
            return
        }

        val attributes = CallAttributesCompat(
            displayName = session.contactName,
            address = Uri.parse("tel:${session.phoneNumber}"),
            direction = if (incoming) {
                CallAttributesCompat.DIRECTION_INCOMING
            } else {
                CallAttributesCompat.DIRECTION_OUTGOING
            },
            callType = CallAttributesCompat.CALL_TYPE_VIDEO_CALL,
            callCapabilities = CallAttributesCompat.SUPPORTS_SET_INACTIVE
        )

        applicationScope.launch {
            try {
                callsManager.addCall(
                    attributes,
                    onAnswer = {
                        session.listener?.onTelecomAnswerRequested(session.callId)
                            ?: launchIncomingActivity(session)
                    },
                    onDisconnect = { cause ->
                        session.listener?.onTelecomDisconnectRequested(
                            session.callId,
                            cause.code
                        ) ?: finishWithoutActivity(session, cause.code)
                    },
                    onSetActive = {
                        session.listener?.onTelecomSetActiveRequested(session.callId)
                    },
                    onSetInactive = {
                        session.listener?.onTelecomSetInactiveRequested(session.callId)
                    }
                ) {
                    session.registered = true
                    launch {
                        isMuted.collectLatest { muted ->
                            session.listener?.onTelecomMuteChanged(session.callId, muted)
                        }
                    }
                    launch {
                        for (command in session.commands) {
                            when (command) {
                                Command.Answer -> answer(CallAttributesCompat.CALL_TYPE_VIDEO_CALL)
                                Command.SetActive -> {
                                    session.active = true
                                    setActive()
                                }
                                Command.SetInactive -> {
                                    session.active = false
                                    setInactive()
                                }
                                is Command.Disconnect -> {
                                    disconnect(command.cause)
                                    break
                                }
                            }
                        }
                    }
                }
            } catch (error: Exception) {
                Log.e(TAG, "Telecom rejected call ${session.callId}", error)
            } finally {
                session.registered = false
                session.commands.close()
                sessions.remove(session.callId, session)
            }
        }
    }

    private fun send(callId: String, command: Command) {
        val session = sessions[normalize(callId)] ?: return
        session.commands.trySend(command)
    }

    private fun launchIncomingActivity(session: Session) {
        if (!session.incoming) {
            return
        }
        val intent = Intent(appContext, VideoCallActivity::class.java)
            .putExtra(MainActivity.EXTRA_NAME, session.contactName)
            .putExtra(MainActivity.EXTRA_PHONE_NUMBER, session.phoneNumber)
            .putExtra(MainActivity.EXTRA_CALL_ID, session.callId)
            .putExtra(MainActivity.EXTRA_INCOMING_CALL, true)
            .putExtra(MainActivity.EXTRA_AUTO_ACCEPT_INCOMING_CALL, true)
            .putExtra(VideoCallActivity.EXTRA_ANSWERED_BY_TELECOM, true)
            .putExtra(
                MainActivity.EXTRA_END_CURRENT_AND_ACCEPT,
                ActiveCallTracker.hasDifferentActiveCall(session.callId)
            )
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        appContext.startActivity(intent)
    }

    private fun finishWithoutActivity(session: Session, disconnectCode: Int) {
        val status = when (disconnectCode) {
            DisconnectCause.REJECTED -> "declined"
            DisconnectCause.MISSED -> "missed"
            else -> when {
                session.active -> "ended"
                session.incoming -> "declined"
                else -> "missed"
            }
        }
        FirebaseCallRepository.markCallInviteStatus(appContext, session.callId, status)
        CallSessionGuard.suppressFinishedCall(appContext, session.callId)
        appContext.getSystemService(android.app.NotificationManager::class.java)
            .cancel(IncomingCallNotificationIds.forCallId(session.callId))
        if (ActiveCallTracker.isActive(session.callId)) {
            ActiveCallTracker.clear(session.callId)
            CallForegroundService.stop(appContext)
        }
    }

    private fun normalize(value: String?): String = value?.trim().orEmpty()
}
