/*
 * This file is part of OpenMUC.
 * For more information visit http://www.openmuc.org
 *
 * You are free to use code of this sample file in any
 * way you like and without any restrictions.
 *
 */
package org.openmuc.framework.app.simpledemo

import org.openmuc.framework.data.DoubleValue
import org.openmuc.framework.data.Flag
import org.openmuc.framework.data.Record
import org.openmuc.framework.data.StringValue
import org.openmuc.framework.dataaccess.Channel
import org.openmuc.framework.dataaccess.DataAccessService
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Deactivate
import org.osgi.service.component.annotations.Reference
import org.slf4j.LoggerFactory
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.*

@Component(service = [])
class SimpleDemoApp {
    var printCounter // for slowing down the output of the console
            = 0

    // With the dataAccessService you can access to your measured and control data of your devices.
    @Reference
    private lateinit var dataAccessService: DataAccessService

    // Channel for accessing data of a channel.
    private var chPowerElectricVehicle: Channel? = null
    private var chPowerPhotovoltaics: Channel? = null
    private var chPowerGrid: Channel? = null
    private var chEvStatus: Channel? = null
    private var chEnergyExported: Channel? = null
    private var chEnergyImported: Channel? = null
    private var energyExportedKWh = 0.0
    private var energyImportedKWh = 0.0
    private var updateTimer: Timer? = null

    /**
     * Every app needs one activate method. Is is called at begin. Here you can configure all you need at start of your
     * app. The Activate method can block the start of your OpenMUC, f.e. if you use Thread.sleep().
     */
    @Activate
    private fun activate() {
        logger.info("Activating Demo App")
        init()
    }

    /**
     * Every app needs one deactivate method. It handles the shutdown of your app e.g. closing open streams.
     */
    @Deactivate
    private fun deactivate() {
        logger.info("Deactivating Demo App")
        logger.info("DemoApp thread interrupted: will stop")
        updateTimer!!.cancel()
        updateTimer!!.purge()
    }

    /**
     * application logic
     */
    private fun init() {
        logger.info("Demo App started running...")
        initializeChannels()

        // Example to demonstrate the possibility of individual settings of each channel
        logger.info("Settings of the PV system: {}", chPowerPhotovoltaics!!.settings)
        applyListener()
        initUpdateTimer()
    }

    /**
     * Initialize channel objects
     */
    private fun initializeChannels() {
        chPowerElectricVehicle = dataAccessService!!.getChannel(ID_POWER_ELECTRIC_VEHICLE)
        chPowerGrid = dataAccessService.getChannel(ID_POWER_GRID)
        chPowerPhotovoltaics = dataAccessService.getChannel(ID_POWER_PHOTOVOLTAICS)
        chEvStatus = dataAccessService.getChannel(ID_STATUS_ELECTRIC_VEHICLE)
        chEnergyExported = dataAccessService.getChannel(ID_ENERGY_EXPORTED)
        chEnergyImported = dataAccessService.getChannel(ID_ENERGY_IMPORTED)
    }

    /**
     * Apply a RecordListener to get notified if a new value is available for a channel
     */
    private fun applyListener() {
        chPowerGrid!!.addListener { record: Record ->
            if (record.value != null) {
                updateEnergyChannels(record)
            }
        }
    }

    private fun initUpdateTimer() {
        updateTimer = Timer("EV-Status Update")
        val task: TimerTask = object : TimerTask() {
            override fun run() {
                updateEvStatusChannel()
            }
        }
        updateTimer!!.scheduleAtFixedRate(
            task,
            SECONDS_PER_INTERVAL.toLong() * 1000,
            SECONDS_PER_INTERVAL.toLong() * 1000
        )
    }

    /**
     * Calculate energy imported and exported from current grid power. (Demonstrates how to access the latest record of
     * a channel and how to set it.)
     *
     * @param gridPowerRecord
     */
    private fun updateEnergyChannels(gridPowerRecord: Record) {
        val gridPower = gridPowerRecord.value!!.asDouble()
        logger.info("home1: current grid power = $gridPower kW")
        val energyOfInterval = Math.abs(gridPower) * HOUR_BASED_INTERVAL_TIME
        val now = System.currentTimeMillis()
        if (gridPower >= 0) {
            energyImportedKWh += energyOfInterval
        } else {
            energyExportedKWh += energyOfInterval
        }
        val exportDouble = DoubleValue(DF.format(energyExportedKWh).toDouble())
        val exportRecord = Record(exportDouble, now, Flag.VALID)
        chEnergyExported!!.latestRecord = exportRecord
        val importDouble = DoubleValue(DF.format(energyImportedKWh).toDouble())
        val importRecord = Record(importDouble, now, Flag.VALID)
        chEnergyImported!!.latestRecord = importRecord
    }

    /**
     * Checks if the electric vehicle is charging (Demonstrates how to access a value from a channel and how to set a
     * value/record)
     */
    private fun updateEvStatusChannel() {
        val evPower: Double
        var status = "idle"

        // get current value of the electric vehicle power channel
        val lastRecord = chPowerElectricVehicle!!.latestRecord
        if (lastRecord != null) {
            val value = lastRecord.value
            if (value != null) {
                evPower = chPowerElectricVehicle!!.latestRecord!!.value!!.asDouble()
                if (evPower > STANDBY_POWER_CHARGING_STATION) {
                    status = "charging"
                }
                // set value for virtual channel
                val newRecord = Record(
                    StringValue(
                        status
                    ), System.currentTimeMillis(), Flag.VALID
                )
                chEvStatus!!.latestRecord = newRecord
            }
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(SimpleDemoApp::class.java)
        private val DFS = DecimalFormatSymbols.getInstance(Locale.US)
        private val DF = DecimalFormat("#0.000", DFS)

        // ChannelIDs, see conf/channel.xml
        private const val ID_POWER_ELECTRIC_VEHICLE = "power_electric_vehicle"
        private const val ID_POWER_GRID = "power_grid"
        private const val ID_POWER_PHOTOVOLTAICS = "power_photovoltaics"
        private const val ID_STATUS_ELECTRIC_VEHICLE = "status_electric_vehicle"
        private const val ID_ENERGY_EXPORTED = "energy_exported"
        private const val ID_ENERGY_IMPORTED = "energy_imported"
        private const val STANDBY_POWER_CHARGING_STATION = 0.020

        // for conversion from power (kW) to energy (kWh)
        private const val SECONDS_PER_HOUR = 3600.0
        private const val SECONDS_PER_INTERVAL = 5.0
        private const val HOUR_BASED_INTERVAL_TIME = SECONDS_PER_INTERVAL / SECONDS_PER_HOUR
    }
}
