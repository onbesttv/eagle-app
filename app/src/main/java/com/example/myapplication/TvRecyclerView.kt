package com.example.myapplication

import android.content.Context
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.View
import androidx.recyclerview.widget.RecyclerView

/**
 * RecyclerView altamente otimizada para Android TV / D-pad.
 * Impede que o foco salte lateralmente para fora da lista (ex: para a lista de pastas/categorias)
 * ao navegar verticalmente com o comando remoto, tanto em navegação rápida como no final da lista.
 */
class TvRecyclerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : RecyclerView(context, attrs, defStyleAttr) {

    private var pendingFocusPosition = -1

    init {
        addOnScrollListener(object : OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                if (pendingFocusPosition != -1) {
                    val vh = findViewHolderForAdapterPosition(pendingFocusPosition)
                    if (vh != null) {
                        pendingFocusPosition = -1
                        vh.itemView.requestFocus()
                    }
                }
            }

            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)
                if (newState == SCROLL_STATE_IDLE && pendingFocusPosition != -1) {
                    val target = pendingFocusPosition
                    pendingFocusPosition = -1
                    val vh = findViewHolderForAdapterPosition(target)
                    if (vh != null) {
                        vh.itemView.requestFocus()
                    } else {
                        scrollToPosition(target)
                        post {
                            findViewHolderForAdapterPosition(target)?.itemView?.requestFocus()
                        }
                    }
                }
            }
        })
    }

    fun resetPendingFocus() {
        pendingFocusPosition = -1
    }

    override fun setAdapter(adapter: Adapter<*>?) {
        resetPendingFocus()
        super.setAdapter(adapter)
    }

    override fun focusSearch(focused: View, direction: Int): View? {
        val result = super.focusSearch(focused, direction)
        if (direction == View.FOCUS_DOWN) {
            // Se o foco encontrou algo fora desta RecyclerView (ex: na lista de categorias):
            // Rejeita terminantemente e mantém o foco no item atual!
            if (result == null || findContainingItemView(result) == null) {
                return focused
            }
        } else if (direction == View.FOCUS_UP) {
            val itemView = findContainingItemView(focused)
            val pos = if (itemView != null) getChildAdapterPosition(itemView) else NO_POSITION
            // Só bloqueia se não for o primeiro item (o pos 0 pode subir para a barra de pesquisa)
            if (pos > 0) {
                if (result == null || findContainingItemView(result) == null) {
                    return focused
                }
            }
        }
        return result
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            val focused = findFocus()
            val itemView = if (focused != null) findContainingItemView(focused) else null

            if (itemView != null) {
                val currentPos = getChildAdapterPosition(itemView)
                val totalCount = adapter?.itemCount ?: 0

                if (currentPos != NO_POSITION && totalCount > 0) {
                    when (event.keyCode) {
                        KeyEvent.KEYCODE_DPAD_DOWN -> {
                            val basePos = if (pendingFocusPosition != -1) pendingFocusPosition else currentPos
                            if (basePos >= totalCount - 1) {
                                // No último item da lista: consome a tecla para que o sistema
                                // nunca faça fallback de busca noutros painéis (como as categorias)
                                return true
                            }
                            val targetPos = (basePos + 1).coerceAtMost(totalCount - 1)
                            val targetVh = findViewHolderForAdapterPosition(targetPos)
                            if (targetVh != null) {
                                pendingFocusPosition = -1
                                targetVh.itemView.requestFocus()
                                return true
                            } else {
                                pendingFocusPosition = targetPos
                                scrollToPosition(targetPos)
                                post {
                                    if (pendingFocusPosition == targetPos) {
                                        val vh = findViewHolderForAdapterPosition(targetPos)
                                        if (vh != null) {
                                            pendingFocusPosition = -1
                                            vh.itemView.requestFocus()
                                        }
                                    }
                                }
                                return true
                            }
                        }

                        KeyEvent.KEYCODE_DPAD_UP -> {
                            val basePos = if (pendingFocusPosition != -1) pendingFocusPosition else currentPos
                            if (basePos > 0) {
                                val targetPos = (basePos - 1).coerceAtLeast(0)
                                val targetVh = findViewHolderForAdapterPosition(targetPos)
                                if (targetVh != null) {
                                    pendingFocusPosition = -1
                                    targetVh.itemView.requestFocus()
                                    return true
                                } else {
                                    pendingFocusPosition = targetPos
                                    scrollToPosition(targetPos)
                                    post {
                                        if (pendingFocusPosition == targetPos) {
                                            val vh = findViewHolderForAdapterPosition(targetPos)
                                            if (vh != null) {
                                                pendingFocusPosition = -1
                                                vh.itemView.requestFocus()
                                            }
                                        }
                                    }
                                    return true
                                }
                            }
                            // Se basePos == 0: deixa passar para permitir focar a barra de pesquisa acima!
                        }
                    }
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }
}
