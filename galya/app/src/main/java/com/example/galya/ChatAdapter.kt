package com.example.galya

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide 

class ChatAdapter(private val messages: MutableList<Message>) :
    RecyclerView.Adapter<ChatAdapter.MessageViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_message, parent, false)
        return MessageViewHolder(view)
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        val message = messages[position]
        holder.bind(message, position)
    }

    override fun getItemCount() = messages.size

    fun addMessage(message: Message) {
        messages.add(message)
        notifyItemInserted(messages.size - 1)
    }

    class MessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val textUser: TextView = itemView.findViewById(R.id.textMessage)
        private val textAssistant: TextView = itemView.findViewById(R.id.textMessageAssistant)
        private val imageAssistant: ImageView = itemView.findViewById(R.id.imageAssistant)
        private val imageUser: ImageView = itemView.findViewById(R.id.imageUser)

        fun bind(message: Message, position: Int) {
            textUser.visibility = View.GONE
            textAssistant.visibility = View.GONE
            imageAssistant.visibility = View.GONE
            imageUser.visibility = View.GONE

            if (message.isUser) {
                if (message.text.isNotEmpty()) {
                    textUser.text = message.text
                    textUser.visibility = View.VISIBLE
                } else if (message.imageUrl != null) {
                    // Если нужно отображать изображение от пользователя (например, загруженное фото)
                    imageUser.visibility = View.VISIBLE
                    Glide.with(itemView.context).load(message.imageUrl).into(imageUser)
                }
            } else {
                if (message.text.isNotEmpty()) {
                    textAssistant.text = message.text
                    textAssistant.visibility = View.VISIBLE
                } else if (message.imageUrl != null) {
                    imageAssistant.visibility = View.VISIBLE
                    Glide.with(itemView.context).load(message.imageUrl).into(imageAssistant)
                }
            }

            itemView.setOnLongClickListener {
                val textToCopy = if (message.isUser) message.text else message.text
                if (textToCopy.isNotEmpty()) {
                    val clipboard = itemView.context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText("message", textToCopy)
                    clipboard.setPrimaryClip(clip)
                    Toast.makeText(itemView.context, "Текст скопирован", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(itemView.context, "Нет текста для копирования", Toast.LENGTH_SHORT).show()
                }
                true
            }
        }
    }
}