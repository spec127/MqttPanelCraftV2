package com.example.mqttpanelcraft

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioButton
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.mqttpanelcraft.utils.LanguageCatalog
import com.example.mqttpanelcraft.utils.LanguageOption
import com.example.mqttpanelcraft.utils.LocaleManager
import com.example.mqttpanelcraft.utils.OnboardingCoordinator
import com.google.android.material.button.MaterialButton
import java.util.Locale

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

        val continueBtn = findViewById<MaterialButton>(R.id.btnLanguageContinue)
        val list = findViewById<RecyclerView>(R.id.rvLanguages)
        list.layoutManager = LinearLayoutManager(this)
        val adapter = LanguageAdapter(LanguageCatalog.options, suggestedIndex()) { _ ->
            continueBtn.isEnabled = true
        }
        list.adapter = adapter
        if (adapter.selectedIndex >= 0) continueBtn.isEnabled = true

        continueBtn.setOnClickListener {
            val option = adapter.selectedOption() ?: return@setOnClickListener
            OnboardingCoordinator.markLanguageGateDone(this)
            LocaleManager.setLocale(this, option.code)
            finish()
        }
    }

    private fun suggestedIndex(): Int {
        val language = Locale.getDefault().language
        val country = Locale.getDefault().country
        val code = when {
            language.equals("zh", true) && country.equals("CN", true) -> LocaleManager.CODE_CN
            language.equals("zh", true) -> LocaleManager.CODE_ZH
            else -> LocaleManager.CODE_EN
        }
        return LanguageCatalog.options.indexOfFirst { it.code == code }.coerceAtLeast(0)
    }

    private class LanguageAdapter(
        private val items: List<LanguageOption>,
        initialIndex: Int,
        private val onSelect: (LanguageOption) -> Unit
    ) : RecyclerView.Adapter<LanguageAdapter.Holder>() {

        var selectedIndex: Int = initialIndex
            private set

        class Holder(view: View) : RecyclerView.ViewHolder(view) {
            val name: TextView = view.findViewById(R.id.tvLanguageName)
            val radio: RadioButton = view.findViewById(R.id.radioLanguage)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_language_option, parent, false)
            return Holder(view)
        }

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val item = items[position]
            val selected = position == selectedIndex
            holder.name.text = item.nativeName
            holder.radio.isChecked = selected
            holder.itemView.isSelected = selected
            holder.itemView.setBackgroundColor(
                if (selected) {
                    ContextCompat.getColor(holder.itemView.context, R.color.language_row_selected)
                } else {
                    android.graphics.Color.TRANSPARENT
                }
            )
            holder.itemView.setOnClickListener {
                val pos = holder.adapterPosition
                if (pos == RecyclerView.NO_POSITION) return@setOnClickListener
                val old = selectedIndex
                selectedIndex = pos
                if (old >= 0) notifyItemChanged(old)
                notifyItemChanged(selectedIndex)
                onSelect(items[pos])
            }
        }

        override fun getItemCount(): Int = items.size

        fun selectedOption(): LanguageOption? = items.getOrNull(selectedIndex)
    }
}
