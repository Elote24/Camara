package com.rockbass2560.mycamera

import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.fragment.app.Fragment
import java.io.File

class ImagePageFragment(private val photo: File) : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_image_page, container, false)

        val photoImageView = view.findViewById<ImageView>(R.id.photo_imageview)
        val bitmap = BitmapFactory.decodeFile(photo.absolutePath)
        photoImageView.setImageBitmap(bitmap)

        return view
    }

}