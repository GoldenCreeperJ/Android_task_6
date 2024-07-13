package com.example.sourcecode

import android.os.Build
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.annotation.RequiresApi
import androidx.lifecycle.LifecycleOwner
import com.example.sourcecode.databinding.ImageItemBinding

@RequiresApi(Build.VERSION_CODES.O)
class ImageAdapter(private var imageList:List<String>):SCRecycleView.SCAdapter<ImageAdapter.ViewHolder>() {
    inner class ViewHolder(val binding: ImageItemBinding) : SCRecycleView.SCViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        ViewHolder(ImageItemBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindingViewHolder(holder: ViewHolder, position: Int) {
        Glide.with(holder.binding.root.context as LifecycleOwner)
            .load(imageList[position]).into(holder.binding.imageView)
    }

    override fun getItemCount(): Int = imageList.size

    fun refresh(){
        imageList=imageList.shuffled()
        notifyDataSetChanged()
    }
}