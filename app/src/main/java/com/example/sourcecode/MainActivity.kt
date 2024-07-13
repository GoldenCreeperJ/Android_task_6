package com.example.sourcecode

import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import com.example.sourcecode.databinding.ActivityMainBinding


class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(LayoutInflater.from(this))
        setContentView(binding.root)

        binding.imageList.layoutManager = SCRecycleView.SCLayoutManager(this).apply {
            orientation = SCRecycleView.SCLayoutManager.VERTICAL
        }

        binding.imageList.adapter = ImageAdapter(
            listOf(
                "https://img.zcool.cn/community/01b1d5563eca0a32f87512f61f3d69.jpg@1280w_1l_2o_100sh.jpg",
                "https://img.zcool.cn/community/01b2945b0663dda801218cf4617993.jpg@3000w_1l_0o_100sh.jpg",
                "https://uploadfile.bizhizu.cn/2017/0701/20170701031428774.jpg",
            )
        )

        binding.swipeRefreshLayout.setRefreshFunction {
            (binding.imageList.adapter as ImageAdapter).refresh()
        }
    }
}