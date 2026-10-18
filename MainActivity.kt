package com.example.fechar

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private data class Item(
        val label: String,
        val pkg: String,
        val suspeito: Boolean = false,
        val origem: String = ""
    )

    private val lojasConfiaveis = mapOf(
        "com.android.vending" to "Play Store",
        "com.sec.android.app.samsungapps" to "Galaxy Store",
        "com.xiaomi.mipicks" to "Xiaomi GetApps",
        "com.huawei.appmarket" to "Huawei AppGallery",
        "com.amazon.venezia" to "Amazon Appstore"
    )

    private val prefs by lazy { getSharedPreferences("fechar", Context.MODE_PRIVATE) }
    private val keep by lazy { prefs.getStringSet("keep", emptySet())!!.toMutableSet() }

    private lateinit var resumo: TextView
    private lateinit var acao: Button
    private lateinit var lista: ListView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val abaFechar = Button(this).apply { text = "Fechar apps" }
        val abaVerificar = Button(this).apply { text = "Verificar origem" }
        val abas = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            addView(abaFechar, lp)
            addView(abaVerificar, lp)
        }

        resumo = TextView(this).apply { textSize = 16f; setPadding(0, 16, 0, 16) }
        acao = Button(this)
        lista = ListView(this)

        abaFechar.setOnClickListener { mostrarFechar() }
        abaVerificar.setOnClickListener { mostrarVerificador() }

        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 96, 24, 24)
            addView(abas)
            addView(resumo)
            addView(acao)
            addView(lista)
        })

        mostrarFechar()
    }

    // ---------- Aba 1: fechar apps em segundo plano ----------
    private fun mostrarFechar() {
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = packageManager.queryIntentActivities(launcher, 0)
            .map { Item(it.loadLabel(packageManager).toString(), it.activityInfo.packageName) }
            .filter { it.pkg != packageName }
            .distinctBy { it.pkg }
            .sortedBy { it.label.lowercase() }

        resumo.text = "Marque os apps que quer MANTER abertos. Os outros serão fechados."

        lista.clearChoices()
        lista.choiceMode = ListView.CHOICE_MODE_MULTIPLE
        lista.adapter = ArrayAdapter(
            this, android.R.layout.simple_list_item_multiple_choice, apps.map { it.label }
        )
        apps.forEachIndexed { i, app -> lista.setItemChecked(i, app.pkg in keep) }
        lista.setOnItemClickListener { _, _, pos, _ ->
            val pkg = apps[pos].pkg
            if (lista.isItemChecked(pos)) keep.add(pkg) else keep.remove(pkg)
            prefs.edit().putStringSet("keep", keep.toSet()).apply()
        }

        acao.visibility = View.VISIBLE
        acao.text = "Fechar apps em segundo plano"
        acao.setOnClickListener {
            val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val alvos = apps.filter { it.pkg !in keep }
            alvos.forEach { am.killBackgroundProcesses(it.pkg) }
            Toast.makeText(this, "${alvos.size} apps encerrados", Toast.LENGTH_SHORT).show()
        }
    }

    // ---------- Aba 2: verificar apps que não são originais do celular ----------
    private fun mostrarVerificador() {
        val pm = packageManager
        val itens = pm.getInstalledApplications(0)
            .filter { app ->
                val sistema = app.flags and ApplicationInfo.FLAG_SYSTEM != 0
                val atualizado = app.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP != 0
                !(sistema || atualizado) && app.packageName != packageName
            }
            .map { app ->
                val instalador = if (Build.VERSION.SDK_INT >= 30)
                    pm.getInstallSourceInfo(app.packageName).installingPackageName
                else
                    @Suppress("DEPRECATION") pm.getInstallerPackageName(app.packageName)
                val loja = lojasConfiaveis[instalador]
                Item(
                    pm.getApplicationLabel(app).toString(),
                    app.packageName,
                    loja == null,
                    loja ?: "FORA DE LOJA (APK manual)"
                )
            }
            .sortedWith(compareByDescending<Item> { it.suspeito }.thenBy { it.label.lowercase() })

        resumo.text = "${itens.size} apps não originais do celular\n" +
            "${itens.count { it.suspeito }} instalados fora de loja\n" +
            "Toque num app para ver detalhes e desinstalar."

        acao.visibility = View.GONE

        lista.clearChoices()
        lista.choiceMode = ListView.CHOICE_MODE_NONE
        lista.adapter = ArrayAdapter(
            this, android.R.layout.simple_list_item_1,
            itens.map { (if (it.suspeito) "⚠ " else "✓ ") + it.label + "\n    " + it.origem }
        )
        lista.setOnItemClickListener { _, _, pos, _ ->
            startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .setData(Uri.parse("package:" + itens[pos].pkg))
            )
        }
    }
}
