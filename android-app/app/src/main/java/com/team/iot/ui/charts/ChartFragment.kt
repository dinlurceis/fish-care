package com.team.iot.ui.charts

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import com.google.android.material.tabs.TabLayout
import com.htn.fishcare.BuildConfig
import com.htn.fishcare.databinding.FragmentChartBinding
import com.team.iot.repository.SensorData
import com.team.iot.viewmodel.ChartViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@AndroidEntryPoint
class ChartFragment : Fragment() {

    private var _binding: FragmentChartBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ChartViewModel by viewModels()

    private lateinit var lineChart: LineChart
    private val dateFormat = SimpleDateFormat("dd/MM", Locale("vi", "VN"))

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentChartBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        lineChart = binding.lineChart
        setupLineChart()
        setupTabListener()
        setupButtonListener()
        setupDebugMenu()
        observeViewModel()

        // Load initial data (7 days)
        viewModel.loadChartData(7)
    }

    private fun setupLineChart() {
        with(lineChart) {
            description.isEnabled = false
            setDrawGridBackground(false)
            setTouchEnabled(true)
            isDragEnabled = true
            isScaleXEnabled = true
            isScaleYEnabled = true

            // Configure XAxis
            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                setDrawGridLines(false)
                valueFormatter = object : ValueFormatter() {
                    override fun getFormattedValue(value: Float): String {
                        val timestamp = value.toLong()
                        return dateFormat.format(Date(timestamp))
                    }
                }
            }

            // Configure YAxis
            axisLeft.apply {
                setDrawGridLines(true)
                axisMinimum = 0f
            }
            axisRight.isEnabled = false

            // Legend at bottom
            legend.apply {
                isEnabled = true
                textSize = 10f
                yOffset = 8f
            }
        }
    }

    private fun setupTabListener() {
        binding.tabPeriod.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                val days = if (tab?.position == 0) 7 else 30
                viewModel.loadChartData(days)
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun setupButtonListener() {
        binding.btnAnalyze.setOnClickListener {
            viewModel.analyzeWithAI()
        }
    }

    private fun setupDebugMenu() {
        if (!BuildConfig.DEBUG) return

        // Add menu for debug features
        requireActivity().addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menu.add(0, 1, 0, "Seed Fake Data")
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                return when (menuItem.itemId) {
                    1 -> {
                        viewModel.seedFakeData()
                        true
                    }
                    else -> false
                }
            }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)

        // Long-click to seed fake data
        binding.root.setOnLongClickListener {
            viewModel.seedFakeData()
            true
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                // Observe chart data
                launch {
                    viewModel.chartDataState.collect { sensorDataList ->
                        updateLineChart(sensorDataList)
                    }
                }

                // Observe average TDS
                launch {
                    viewModel.avgTdsState.collect { avgTds ->
                        binding.tvAvgTds.text = String.format("%.0f", avgTds)
                    }
                }

                // Observe average temperature
                launch {
                    viewModel.avgTempState.collect { avgTemp ->
                        binding.tvAvgTemp.text = String.format("%.1f°C", avgTemp)
                    }
                }

                // Observe average turbidity
                launch {
                    viewModel.avgTurbidityState.collect { avgTurbidity ->
                        binding.tvAvgTurbidity.text = String.format("%.1f", avgTurbidity)
                    }
                }

                // Observe loading state
                launch {
                    viewModel.isLoadingState.collect { isLoading ->
                        binding.progressAI.visibility = if (isLoading) View.VISIBLE else View.GONE
                        binding.btnAnalyze.isEnabled = !isLoading
                    }
                }

                // Observe AI result
                launch {
                    viewModel.aiResultState.collect { result ->
                        if (result.isNotEmpty()) {
                            binding.cardAIResult.visibility = View.VISIBLE
                            binding.tvAIResult.text = result
                        } else {
                            binding.cardAIResult.visibility = View.GONE
                        }
                    }
                }
            }
        }
    }

    private fun updateLineChart(sensorDataList: List<SensorData>) {
        if (sensorDataList.isEmpty()) {
            lineChart.clear()
            return
        }

        val tdsEntries = mutableListOf<Entry>()
        val tempEntries = mutableListOf<Entry>()
        val turbidityEntries = mutableListOf<Entry>()

        sensorDataList.forEach { data ->
            val timestamp = data.timestamp.toFloat()
            tdsEntries.add(Entry(timestamp, data.tds.toFloat()))
            tempEntries.add(Entry(timestamp, data.temperature.toFloat()))
            turbidityEntries.add(Entry(timestamp, data.turbidity.toFloat()))
        }

        // Create data sets with different colors
        val tdsDataSet = LineDataSet(tdsEntries, "TDS").apply {
            color = android.graphics.Color.parseColor("#FF5722") // Orange
            setCircleColor(android.graphics.Color.parseColor("#FF5722"))
            lineWidth = 2f
            circleRadius = 3f
            mode = LineDataSet.Mode.CUBIC_BEZIER
        }

        val tempDataSet = LineDataSet(tempEntries, "Nhiệt độ").apply {
            color = android.graphics.Color.parseColor("#FF9800") // Deep Orange
            setCircleColor(android.graphics.Color.parseColor("#FF9800"))
            lineWidth = 2f
            circleRadius = 3f
            mode = LineDataSet.Mode.CUBIC_BEZIER
        }

        val turbidityDataSet = LineDataSet(turbidityEntries, "Độ đục").apply {
            color = android.graphics.Color.parseColor("#2196F3") // Blue
            setCircleColor(android.graphics.Color.parseColor("#2196F3"))
            lineWidth = 2f
            circleRadius = 3f
            mode = LineDataSet.Mode.CUBIC_BEZIER
        }

        val lineData = LineData(tdsDataSet, tempDataSet, turbidityDataSet)
        lineChart.data = lineData
        lineChart.invalidate()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
