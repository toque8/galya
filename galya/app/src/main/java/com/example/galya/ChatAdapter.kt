package com.example.galya

import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class ChatAdapter(private val messages: MutableList<Message>) :
    RecyclerView.Adapter<ChatAdapter.MessageViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_message, parent, false)
        return MessageViewHolder(view)
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        val message = messages[position]
        holder.bind(message)
    }

    override fun getItemCount() = messages.size

    fun addMessage(message: Message) {
        messages.add(message)
        notifyItemInserted(messages.size - 1)
    }

    class MessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val textUser: TextView = itemView.findViewById(R.id.textMessage)
        private val textAssistant: TextView = itemView.findViewById(R.id.textMessageAssistant)

        fun bind(message: Message) {
            if (message.isUser) {
                textUser.text = message.text
                textUser.visibility = View.VISIBLE
                textAssistant.visibility = View.GONE
            } else {
                textAssistant.text = message.text
                textAssistant.visibility = View.VISIBLE
                textUser.visibility = View.GONE
            }
        }
    }
}