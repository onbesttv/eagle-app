package com.example.myapplication

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class CanalAdapter(
    private var listaOriginal: List<Canal>,
    private val onCanalClick: (Canal) -> Unit
) : RecyclerView.Adapter<CanalAdapter.CanalViewHolder>() {

    private var listaFiltrada: List<Canal> = listaOriginal

    class CanalViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvNome: TextView = view.findViewById(android.R.id.text1)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CanalViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(android.R.layout.simple_list_item_1, parent, false)
        return CanalViewHolder(view)
    }

    override fun onBindViewHolder(holder: CanalViewHolder, position: Int) {
        val canal = listaFiltrada[position]
        holder.tvNome.text = canal.name
        holder.tvNome.setTextColor(0xFFFFFFFF.toInt()) // Cor branca para contraste com fundo escuro
        holder.itemView.setOnClickListener { onCanalClick(canal) }
    }

    override fun getItemCount(): Int = listaFiltrada.size

    fun atualizarLista(novaLista: List<Canal>) {
        listaOriginal = novaLista
        listaFiltrada = novaLista
        notifyDataSetChanged()
    }

    fun filtrar(texto: String) {
        listaFiltrada = if (texto.isEmpty()) {
            listaOriginal
        } else {
            listaOriginal.filter { it.name.contains(texto, ignoreCase = true) }
        }
        notifyDataSetChanged()
    }
}