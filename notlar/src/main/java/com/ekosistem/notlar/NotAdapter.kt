package com.ekosistem.notlar

import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class NotAdapter(
    private val onTikla: (Not) -> Unit,
    private val onUzunBas: (Not) -> Unit
) : RecyclerView.Adapter<NotAdapter.Tutucu>() {

    private var notlar: List<Not> = emptyList()

    fun guncelle(yeni: List<Not>) {
        notlar = yeni
        notifyDataSetChanged()
    }

    class Tutucu(v: View) : RecyclerView.ViewHolder(v) {
        val baslik: TextView = v.findViewById(R.id.notBaslik)
        val ozet: TextView = v.findViewById(R.id.notOzet)
        val tarih: TextView = v.findViewById(R.id.notTarih)
        val sabit: ImageView = v.findViewById(R.id.sabitIkon)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Tutucu {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_not, parent, false)
        return Tutucu(v)
    }

    override fun getItemCount(): Int = notlar.size

    override fun onBindViewHolder(t: Tutucu, pozisyon: Int) {
        val not = notlar[pozisyon]
        t.baslik.text = not.baslik
        t.ozet.text = not.ozet
        t.ozet.visibility = if (not.ozet.isBlank()) View.GONE else View.VISIBLE
        t.tarih.text = if (not.degistirilme > 0) {
            DateUtils.getRelativeTimeSpanString(not.degistirilme)
        } else ""
        t.sabit.visibility = if (not.sabit) View.VISIBLE else View.GONE
        t.itemView.setOnClickListener { onTikla(not) }
        t.itemView.setOnLongClickListener { onUzunBas(not); true }
    }
}
