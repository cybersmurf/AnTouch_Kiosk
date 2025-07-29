package cz.emistr.antouchkiosk

import android.graphics.Bitmap
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

data class IdentificationResult(val user: FingerprintUser?, val score: Int)

class SharedFingerprintViewModel : ViewModel() {

    private val _isConnected = MutableLiveData<Boolean>(false)
    val isConnected: LiveData<Boolean> get() = _isConnected

    private val _infoText = MutableLiveData<String>()
    val infoText: LiveData<String> get() = _infoText

    private val _capturedImage = MutableLiveData<Bitmap?>()
    val capturedImage: LiveData<Bitmap?> get() = _capturedImage

    private val _identificationResult = MutableLiveData<IdentificationResult>()
    val identificationResult: LiveData<IdentificationResult> get() = _identificationResult

    private val _registrationStep = MutableLiveData<Pair<Int, Int>>()
    val registrationStep: LiveData<Pair<Int, Int>> get() = _registrationStep

    private val _registrationComplete = MutableLiveData<String>()
    val registrationComplete: LiveData<String> get() = _registrationComplete

    private val _refreshUserListEvent = MutableLiveData<Boolean>()
    val refreshUserListEvent: LiveData<Boolean> get() = _refreshUserListEvent

    fun setConnected(status: Boolean) { _isConnected.postValue(status) }
    fun setInfoText(text: String) { _infoText.postValue(text) }
    fun setCapturedImage(image: Bitmap?) { _capturedImage.postValue(image) }
    fun setIdentificationResult(user: FingerprintUser?, score: Int) { _identificationResult.postValue(IdentificationResult(user, score)) }
    fun setRegistrationStep(current: Int, total: Int) { _registrationStep.postValue(Pair(current, total)) }

    fun setRegistrationComplete(workerId: String) {
        _registrationComplete.postValue(workerId)
        _refreshUserListEvent.postValue(true)
    }

    fun onRefreshUserListComplete() {
        _refreshUserListEvent.value = false
    }
}