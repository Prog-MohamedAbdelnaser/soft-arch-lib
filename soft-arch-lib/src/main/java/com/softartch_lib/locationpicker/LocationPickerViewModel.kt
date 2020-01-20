package com.softartch_lib.locationpicker

import android.content.Context
import android.graphics.Typeface
import android.text.style.StyleSpan
import android.util.Log
import android.widget.Filter
import android.widget.Filterable
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.softartch_lib.component.RequestDataState
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.tasks.Tasks
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.AutocompleteSessionToken
import com.google.android.libraries.places.api.model.TypeFilter
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest
import com.google.android.libraries.places.api.net.PlacesClient
import com.google.maps.android.SphericalUtil
import com.softartch_lib.locationpicker.LocationPickerFragmentWithSearchBar.Companion.GOOGLE_API_KEY
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import io.reactivex.subjects.PublishSubject
import org.koin.android.ext.koin.androidApplication
import java.util.concurrent.TimeUnit

class LocationPickerViewModel(private val locationAddressUseCase: LocationAddressUseCase,
                               private val token: AutocompleteSessionToken,
                               private val contect:Context) : ViewModel() {

    private val disposables=CompositeDisposable()
    var lastSelectedLocation :LatLng?=null
    private var targetAddress: String? = null
    private var targetLocationAddress: LocationAddress?=null

    var locationAddressLiveDataState =MutableLiveData<RequestDataState<LocationAddress>>()

    var placesSearchLiveDataState =MutableLiveData<RequestDataState<ArrayList<PlaceAutoComplete>>>()




    lateinit var placesClient:PlacesClient

    companion object {
        const val ONE_METER = 1
    }

    private val locationAddressSubject = PublishSubject.create<LatLng>()


    init {

        Places.initialize(contect, GOOGLE_API_KEY)
       placesClient = Places.createClient(contect)

        disposables.add(locationAddressSubject
            .filter {
                val filter =
                    if (lastSelectedLocation == null) {
                        true
                    } else {
                        SphericalUtil.computeDistanceBetween(
                            LatLng(lastSelectedLocation!!.latitude,
                                lastSelectedLocation!!.longitude),
                            LatLng(it.latitude, it.longitude)) > ONE_METER
                    }
                if (filter) {
                    locationAddressLiveDataState.postValue(RequestDataState.LoadingShow)
                }
                filter
            }
            .debounce(300, TimeUnit.MILLISECONDS)
            .subscribe {
                fetchLocationAddress(it)
            })
    }



    fun onCameraIdle(location: LatLng) {

        Log.i("onCameraIdle","location${location.toString()}")

        if (targetAddress == null) {
            locationAddressSubject.onNext(location)
        } else {
            targetLocationAddress=LocationAddress(location!!.latitude,location.longitude,targetAddress.toString())
            locationAddressLiveDataState.postValue(RequestDataState.Success(targetLocationAddress!!))
        }
    }

    fun fetchLocationAddress(latlng: LatLng?) {

        lastSelectedLocation = latlng
        disposables.add(locationAddressUseCase.execute(latlng)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({
    Log.i("Location","fetchLocationAddress$it")
    targetAddress = it
                targetLocationAddress =LocationAddress(latlng!!.latitude,latlng.longitude,it)
    locationAddressLiveDataState.value =RequestDataState.Success(targetLocationAddress!!)

}, {
    it.printStackTrace()
    locationAddressLiveDataState.value = RequestDataState.Error(it)
})
)
}

    fun setTargetAddress(targetAddress: String?) {
        this.targetAddress = targetAddress
    }

    fun setTargetLocationAddress(locationAddress: LocationAddress?) {
        this.targetLocationAddress = locationAddress
        setTargetAddress(targetLocationAddress!!.addressName)
    }

/*    override fun getFilter(): Filter {


        return object : Filter() {
            override fun performFiltering(constraint: CharSequence?): FilterResults {
                val results = FilterResults()
                if (constraint != null) {

                    searchResultList = getPredictions(constraint)
                    if (searchResultList != null) {
                        results.values = searchResultList
                        results.count = searchResultList.size
                    }
                }
                return results
            }

            override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
                onAutoCompleteSearchFinised(searchResultList.size>0)
                placesSearchResultAdapter?.notifyDataSetChanged()
            }

        }
    }*/

     fun filter(constraint: CharSequence,localizationFillter:String=""){
         disposables.add(getPredictions(constraint,localizationFillter)
             .subscribeOn(Schedulers.io())
             .observeOn(AndroidSchedulers.mainThread())
             .doOnSubscribe { placesSearchLiveDataState.value=RequestDataState.LoadingShow }
             .doFinally {placesSearchLiveDataState.value=RequestDataState.LoadingFinished  }
             .subscribe({
                 placesSearchLiveDataState.value=RequestDataState.Success(it)
             },{
                 placesSearchLiveDataState.value=RequestDataState.Error(it)
             }))
     }

    fun getPredictions(constraint: CharSequence,localizationFillter:String): Single<ArrayList<PlaceAutoComplete>> {

       return Single.create<ArrayList<PlaceAutoComplete>> {emitter->

        val STYLE_NORMAL = StyleSpan(Typeface.NORMAL)
        val STYLE_BOLD = StyleSpan(Typeface.BOLD)

        val resultList = ArrayList<PlaceAutoComplete>()

        val request = FindAutocompletePredictionsRequest.builder()
            .setSessionToken(token)
            .setQuery(constraint.toString())
           .setCountry(localizationFillter)
            .setTypeFilter(TypeFilter.ADDRESS)
            .build()


        val autoCompletePredictions = placesClient?.findAutocompletePredictions(request)

        Tasks.await(autoCompletePredictions!!, 60, TimeUnit.SECONDS)

        autoCompletePredictions.addOnSuccessListener {

            if (it.autocompletePredictions.isNullOrEmpty().not()){

                it.autocompletePredictions.iterator().forEach { it ->
                    Log.i("getPredictions","getPredictions ${it.toString()}")
                    resultList.add(PlaceAutoComplete(
                        it.placeId,
                        it.getPrimaryText(STYLE_NORMAL).toString(),
                        it.getFullText(STYLE_BOLD).toString()))
                }
                emitter.onSuccess(resultList)
            }

        }.addOnFailureListener { emitter.onError(it) }

       }
    }

}