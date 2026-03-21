package com.sunfeld.smsgateway

import android.content.Context
import android.content.res.ColorStateList
import android.util.AttributeSet
import com.google.android.material.button.MaterialButton
import com.google.android.material.color.MaterialColors

/**
 * Custom MaterialButton that represents the SMS Gateway install action.
 * Manages three visual states: IDLE, INSTALLING, and ERROR, each with
 * distinct Material 3 styling.
 */
class GatewayInstallButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = com.google.android.material.R.attr.materialButtonStyle
) : MaterialButton(context, attrs, defStyleAttr) {

    enum class State {
        IDLE,
        INSTALLING,
        ERROR
    }

    var state: State = State.IDLE
        set(value) {
            field = value
            applyState()
        }

    var onInstallClick: (() -> Unit)? = null

    init {
        applyState()
        setOnClickListener {
            if (state != State.INSTALLING) {
                onInstallClick?.invoke()
            }
        }
    }

    private fun applyState() {
        when (state) {
            State.IDLE -> {
                text = context.getString(R.string.install_gateway)
                isEnabled = true
                backgroundTintList = ColorStateList.valueOf(
                    MaterialColors.getColor(this, com.google.android.material.R.attr.colorPrimary)
                )
                setTextColor(
                    MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnPrimary)
                )
                alpha = 1.0f
            }
            State.INSTALLING -> {
                text = context.getString(R.string.installing_gateway)
                isEnabled = false
                backgroundTintList = ColorStateList.valueOf(
                    MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurfaceVariant)
                )
                setTextColor(
                    MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant)
                )
                alpha = 0.7f
            }
            State.ERROR -> {
                text = context.getString(R.string.retry_install_gateway)
                isEnabled = true
                backgroundTintList = ColorStateList.valueOf(
                    MaterialColors.getColor(this, com.google.android.material.R.attr.colorError)
                )
                setTextColor(
                    MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnError)
                )
                alpha = 1.0f
            }
        }
    }
}
