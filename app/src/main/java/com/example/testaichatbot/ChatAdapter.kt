package com.example.testaichatbot

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import io.noties.markwon.Markwon

class ChatAdapter(
    private val chatList: MutableList<ChatModel>,
    private val onSpeak: (String) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    override fun getItemViewType(position: Int): Int {
        return if (chatList[position].isBot) 1 else 0
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == 1) {
            val view = inflater.inflate(R.layout.item_bot_chat, parent, false)
            BotViewHolder(view)
        } else {
            val view = inflater.inflate(R.layout.item_user_chat, parent, false)
            UserViewHolder(view)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = chatList[position]
        if (holder is UserViewHolder) {
            holder.tvUser.text = item.message
        } else if (holder is BotViewHolder) {
            val markwon = Markwon.create(holder.itemView.context)
            markwon.setMarkdown(holder.tvBot, item.message)
            holder.btnSpeaker.setOnClickListener {
                onSpeak(item.message)
            }
        }
    }

    override fun getItemCount(): Int = chatList.size

    class UserViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvUser: TextView = view.findViewById(R.id.tvUserMessage)
    }

    class BotViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvBot: TextView = view.findViewById(R.id.tvBotMessage)
        val btnSpeaker: ImageView = view.findViewById(R.id.btnSpeaker)
    }
}