package com.b4kancs.rxredditdemo.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.b4kancs.rxredditdemo.MainActivity
import com.b4kancs.rxredditdemo.databinding.FragmentHomeBinding

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private lateinit var homeViewModel: HomeViewModel

    // This property is only valid between onCreateView and onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {

        _binding = FragmentHomeBinding.inflate(inflater, container, false)

        homeViewModel = ViewModelProvider(this)[HomeViewModel::class.java]

        return binding.root
    }

    override fun onResume() {
        super.onResume()
        val activity = activity as MainActivity
        homeViewModel.subredditNameLiveData.observe(activity) { subredditName ->
            activity.supportActionBar?.title = subredditName
        }

        homeViewModel.postsLiveData.observe(activity) {
            binding.recyclerPosts.adapter = PostSubredditAdapter(homeViewModel.postsLiveData.value!!, requireContext())
            binding.recyclerPosts.layoutManager = LinearLayoutManager(context)
            binding.progressCircular.visibility = View.GONE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}