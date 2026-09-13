package com.example.mqttpanelcraft

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.mqttpanelcraft.utils.LanguageCatalog
import com.example.mqttpanelcraft.utils.LanguageOption
import com.example.mqttpanelcraft.utils.LocaleManager
import com.example.mqttpanelcraft.utils.OnboardingCoordinator
import com.google.android.material.button.MaterialButton

class LanguageOnboardingActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (OnboardingCoordinator.isLanguageGateDone(this)) {
            finish()
            return
        }
        setContentView(R.layout.activity_language_onboarding)
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() = Unit
            }
        )
        val list = findViewById<RecyclerView>(R.id.rvLanguages)
        list.layoutManager = LinearLayoutManager(this)
        list.adapter = LanguageAdapter(LanguageCatalog.options) { option ->
            OnboardingCoordinator.markLanguageGateDone(this)
            LocaleManager.setLocale(this, option.code)
            finish()
        }
    }

    private class LanguageAdapter(
        private val items: List<LanguageOption>,
        private val onPick: (LanguageOption) -> Unit
    ) : RecyclerView.Adapter<LanguageAdapter.Holder>() {

        class Holder(val button: MaterialButton) : RecyclerView.ViewHolder(button)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_language_option, parent, false) as MaterialButton
            return Holder(view)
        }

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val item = items[position]
            holder.button.text = item.nativeName
            holder.button.setOnClickListener { onPick(item) }
        }

        override fun getItemCount(): Int = items.size
    }
}
