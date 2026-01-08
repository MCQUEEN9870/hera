document.addEventListener('DOMContentLoaded', function() {
    const form = document.getElementById('vehicleRegistrationForm');
    const contactNo = document.getElementById('contactNo');
    const whatsappNo = document.getElementById('whatsappNo');
    const sameAsContact = document.getElementById('sameAsContact');
    const vehiclePhotos = document.getElementById('vehiclePhotos');
    const filePreview = document.getElementById('filePreview');
    const photoCounter = document.querySelector('.photo-counter');
    const photoError = document.querySelector('.photo-error');
    const pincode = document.getElementById('pincode');
    const verifyPincodeBtn = document.getElementById('verifyPincodeBtn');
    const useLocationBtn = document.getElementById('useLocationBtn');
    const vehicleSelectBtn = document.getElementById('vehicleSelectBtn');
    const vehicleDropdown = document.querySelector('.vehicle-dropdown');
    const celebrationOverlay = document.getElementById('celebrationOverlay');
    const stateInput = document.getElementById('state');
    const cityInput = document.getElementById('city');
    const stateClickCapture = document.getElementById('stateClickCapture');
    const vehicleNumberField = document.getElementById('vehicleNumber');
    const vehicleNumberContainer = vehicleNumberField.parentNode;
    
    // Add variable to track pincode verification status
    let isPincodeVerified = false;

    function escapeHtml(value) {
        const s = (value === null || value === undefined) ? '' : String(value);
        return s
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;')
            .replace(/'/g, '&#39;');
    }

    // Toggle bilingual hint on every click
    let pincodeHintToggle = 0;
    
    // Add variable to track if selected vehicle is a manual cart
    let isManualCartSelected = false;
    
    // Add helper text for vehicle number format
    const vehicleNumberHelper = document.createElement('div');
    vehicleNumberHelper.className = 'vehicle-number-helper';
    vehicleNumberHelper.innerHTML = 'Format: XX-00-XX-0000 (e.g., MH-01-AB-1234)';
    vehicleNumberHelper.style.color = '#666';
    vehicleNumberHelper.style.fontSize = '12px';
    vehicleNumberHelper.style.marginTop = '5px';
    vehicleNumberContainer.appendChild(vehicleNumberHelper);
    
    // Set city and state fields as readonly
    cityInput.setAttribute('readonly', 'readonly');
    stateInput.setAttribute('disabled', 'disabled');
    
    // Add visual indication that these fields can't be edited directly
    cityInput.style.backgroundColor = '#f8f8f8';
    stateInput.style.backgroundColor = '#f8f8f8';

    function shakeUseLocationButton() {
        if (!useLocationBtn) return;
        useLocationBtn.classList.remove('shake-attention');
        // Force reflow so animation re-triggers reliably
        void useLocationBtn.offsetWidth;
        useLocationBtn.classList.add('shake-attention');
        window.setTimeout(() => useLocationBtn.classList.remove('shake-attention'), 600);
    }

    function maybeShowPincodeHintToast() {
        // Only guide when pincode is missing/incomplete
        if (!pincode || (pincode.value || '').trim().length >= 6) return false;

        const message = (pincodeHintToggle++ % 2 === 0)
            ? 'कृपया अपना पिनकोड भरें'
            : 'Please Fill/ Enter Your pincode';

        showToast(message, 'error');
        shakeUseLocationButton();
        return true;
    }

    // When user clicks City/State while pincode is not filled, show bilingual toast
    if (cityInput) {
        cityInput.addEventListener('click', function() {
            // If pincode is already verified, don't distract
            if (isPincodeVerified) return;
            maybeShowPincodeHintToast();
        });
    }

    // State select is disabled, so clicks won't fire on it; we capture clicks via an overlay button
    if (stateClickCapture) {
        stateClickCapture.addEventListener('click', function() {
            if (isPincodeVerified) return;
            maybeShowPincodeHintToast();
        });
    }
    
    // Check if manual cart is selected on page load
    const vehicleTypeInputs = document.querySelectorAll('input[name="vehicleType"]');
    vehicleTypeInputs.forEach(input => {
        if (input.checked && input.value === 'Manual Cart (Thel / Rickshaw)') {
            isManualCartSelected = true;
            vehicleNumberContainer.style.display = 'none';
            vehicleNumberField.removeAttribute('required');
        }
    });
    
    // Add a note to inform users
    const locationNote = document.createElement('div');
    locationNote.className = 'location-note';
    locationNote.innerHTML = ' City and State will be auto-filled after pincode verification';
    locationNote.style.color = '#666';
    locationNote.style.fontSize = '12px';
    locationNote.style.marginTop = '5px';
    
    // Insert the note after pincode field
    pincode.parentNode.appendChild(locationNote);

    // Create loading overlay
    const loadingOverlay = document.createElement('div');
    loadingOverlay.className = 'loading-overlay';
    loadingOverlay.innerHTML = `
        <div class="loading-spinner"></div>
        <p>Processing your registration...</p>
    `;
    document.body.appendChild(loadingOverlay);

    // Photo upload elements
    const photoUploadBoxes = document.querySelectorAll('.photo-upload-box');
    const photoUploadModal = document.querySelector('.photo-upload-modal');
    const closeModalBtn = document.querySelector('.close-modal');
    const uploadViewTitle = document.querySelector('.upload-view-title');
    const cameraOption = document.querySelector('.modal-option.camera-option');
    const galleryOption = document.querySelector('.modal-option.gallery-option');
    
    // Hidden file inputs
    const frontViewPhoto = document.getElementById('frontViewPhoto');
    const sideViewPhoto = document.getElementById('sideViewPhoto');
    const backViewPhoto = document.getElementById('backViewPhoto');
    const loadingViewPhoto = document.getElementById('loadingViewPhoto');
    
    
    // Store uploaded photos
    const uploadedPhotos = {
        front: null,
        side: null,
        back: null,
        loading: null
    };
   
  


    
    // Current active view for upload
    let currentUploadView = null;

    // Vehicle dropdown toggle
    vehicleSelectBtn.addEventListener('click', function() {
        vehicleDropdown.classList.toggle('show');
    });

    // Close dropdown when clicking outside
    document.addEventListener('click', function(e) {
        if (!e.target.closest('.vehicle-type-select') && vehicleDropdown.classList.contains('show')) {
            vehicleDropdown.classList.remove('show');
        }
    });

    // Update vehicle select button text when a vehicle type is selected
    vehicleTypeInputs.forEach(input => {
        input.addEventListener('change', function() {
            if (this.checked) {
                vehicleSelectBtn.querySelector('span').textContent = this.value;
                vehicleDropdown.classList.remove('show');
                
                // Check if selected vehicle is a manual cart
                isManualCartSelected = this.value === 'Manual Cart (Thel / Rickshaw)';
                
                // Toggle visibility of vehicle number field based on selection
                if (isManualCartSelected) {
                    vehicleNumberContainer.style.display = 'none';
                    vehicleNumberField.removeAttribute('required');
                } else {
                    vehicleNumberContainer.style.display = 'block';
                    vehicleNumberField.setAttribute('required', 'required');
                }
            }
        });
    });

    // Handle "Same as contact" checkbox
    sameAsContact.addEventListener('change', function() {
        if (this.checked) {
            whatsappNo.value = contactNo.value;
            whatsappNo.disabled = true;
            
            // Also update validation styling
            if (contactNo.value.length === 10) {
                whatsappNo.style.borderColor = '#4CAF50';
                whatsappNo.style.boxShadow = '0 0 5px rgba(76, 175, 80, 0.5)';
            }
        } else {
            whatsappNo.disabled = false;
            whatsappNo.value = '';
            whatsappNo.style.borderColor = '';
            whatsappNo.style.boxShadow = '';
        }
    });

    // Update WhatsApp number when contact number changes and checkbox is checked
    contactNo.addEventListener('input', function() {
        if (sameAsContact.checked) {
            whatsappNo.value = this.value;
        }
    });
    
    // Add a note that WhatsApp number is optional
    const whatsappNote = document.createElement('div');
    whatsappNote.className = 'whatsapp-note';
    whatsappNote.innerHTML = 'WhatsApp number is optional. Leave empty if not applicable.';
    whatsappNote.style.color = '#666';
    whatsappNote.style.fontSize = '12px';
    whatsappNote.style.marginTop = '5px';
    whatsappNo.parentNode.appendChild(whatsappNote);

    // Handle phone number input validation
    [contactNo, whatsappNo].forEach(input => {
        input.addEventListener('input', function() {
            this.value = this.value.replace(/[^0-9]/g, '');
            if (this.value.length > 10) {
                this.value = this.value.slice(0, 10);
            }
            
            // WhatsApp number is optional, so don't show error if empty
            if (this.id === 'whatsappNo' && this.value.length === 0) {
                this.style.borderColor = '';
                this.style.boxShadow = '';
                
                // Remove error message if exists
                const errorMsgId = this.id + 'Error';
                const existingError = document.getElementById(errorMsgId);
                if (existingError) {
                    existingError.remove();
                }
                return;
            }
            
            // Add visual indicator for phone number length
            if (this.value.length === 10) {
                this.style.borderColor = '#4CAF50';
                this.style.boxShadow = '0 0 5px rgba(76, 175, 80, 0.5)';
                
                // Remove error message if exists
                const errorMsgId = this.id + 'Error';
                const existingError = document.getElementById(errorMsgId);
                if (existingError) {
                    existingError.remove();
                }
            } else {
                this.style.borderColor = '#ff5252';
                this.style.boxShadow = '0 0 5px rgba(255, 82, 82, 0.5)';
                
                // Add/update error message
                let errorMsg = document.getElementById(this.id + 'Error');
                if (!errorMsg) {
                    errorMsg = document.createElement('div');
                    errorMsg.id = this.id + 'Error';
                    errorMsg.className = 'error-message';
                    this.parentNode.appendChild(errorMsg);
                }
                errorMsg.textContent = 'Please enter a 10-digit phone number';
            }
        });
        
        // Also validate on blur
        input.addEventListener('blur', function() {
            // Skip validation for empty WhatsApp number since it's optional
            if (this.id === 'whatsappNo' && this.value.length === 0) {
                this.style.borderColor = '';
                this.style.boxShadow = '';
                return;
            }
            
            if (this.value.length > 0 && this.value.length < 10) {
                showToast('Please enter a valid 10-digit phone number', 'error');
                this.focus();
            }
        });
    });

    // Handle pincode input validation
    pincode.addEventListener('input', function() {
        this.value = this.value.replace(/[^0-9]/g, '');
        if (this.value.length > 6) {
            this.value = this.value.slice(0, 6);
        }
        
        // Reset verification status when pincode is changed
        isPincodeVerified = false;
        
        // Add visual indicator for pincode length
        if (this.value.length === 6) {
            this.style.borderColor = '#ff9800';  // Orange to indicate needs verification
            this.style.boxShadow = '0 0 5px rgba(255, 152, 0, 0.5)';
            
            // Update location note
            locationNote.innerHTML = 'Please click "Verify" button to verify pincode';
            locationNote.style.color = '#ff9800';
        } else {
            this.style.borderColor = '#ff5252';
            this.style.boxShadow = '0 0 5px rgba(255, 82, 82, 0.5)';
            
            // Update location note
            locationNote.innerHTML = 'Please enter a valid 6-digit pincode';
            locationNote.style.color = '#ff5252';
            
            // Reset city and state
            cityInput.value = '';
            stateInput.selectedIndex = 0;
        }
    });

    // Extracted verification logic so it can be reused by Use Location flow
    async function performPincodeVerification(pin) {
        const pincodeValue = (pin || pincode.value || '').trim();
        if (!/^\d{6}$/.test(pincodeValue)) {
            showToast('Please enter a valid 6-digit pincode', 'error');
            return false;
        }
        const originalBtnText = verifyPincodeBtn ? verifyPincodeBtn.innerHTML : '';
        if (verifyPincodeBtn) { verifyPincodeBtn.innerHTML = '<i class="fas fa-spinner fa-spin"></i> Verifying...'; verifyPincodeBtn.disabled = true; }
        try {
            const resp = await fetch(`https://api.postalpincode.in/pincode/${pincodeValue}`);
            const data = await resp.json();
            if (data && data[0].Status === 'Success') {
                const postOffice = data[0].PostOffice[0];
                const stateValue = postOffice.State || postOffice.Circle;
                for (let i = 0; i < stateInput.options.length; i++) {
                    if (stateInput.options[i].value === stateValue) { stateInput.selectedIndex = i; break; }
                }
                cityInput.value = postOffice.District || postOffice.Division || postOffice.Region || postOffice.Block;
                // Visual confirmation
                cityInput.style.borderColor = '#4CAF50';
                cityInput.style.boxShadow = '0 0 5px rgba(76, 175, 80, 0.5)';
                stateInput.style.borderColor = '#4CAF50';
                stateInput.style.boxShadow = '0 0 5px rgba(76, 175, 80, 0.5)';
                pincode.style.borderColor = '#4CAF50';
                pincode.style.boxShadow = '0 0 5px rgba(76, 175, 80, 0.5)';
                locationNote.innerHTML = 'Location verified';
                locationNote.style.color = '#4CAF50';
                isPincodeVerified = true;
                showToast('Pincode verified successfully!', 'success');
                return true;
            } else {
                cityInput.value = '';
                stateInput.selectedIndex = 0;
                isPincodeVerified = false;
                pincode.style.borderColor = '#ff5252';
                pincode.style.boxShadow = '0 0 5px rgba(255, 82, 82, 0.5)';
                locationNote.innerHTML = 'Invalid pincode. Please enter a valid pincode';
                locationNote.style.color = '#ff5252';
                showToast('Invalid pincode. Please enter a valid pincode.', 'error');
                return false;
            }
        } catch (e) {
            console.error('Error verifying pincode:', e);
            isPincodeVerified = false;
            locationNote.innerHTML = 'Error verifying pincode. Please try again';
            locationNote.style.color = '#ff5252';
            showToast('Error verifying pincode. Please try again.', 'error');
            return false;
        } finally {
            if (verifyPincodeBtn) { verifyPincodeBtn.innerHTML = originalBtnText; verifyPincodeBtn.disabled = false; }
        }
    }

    // Hook up original Verify button to the shared function
    if (verifyPincodeBtn) verifyPincodeBtn.addEventListener('click', () => { performPincodeVerification(); });

    // Use Location flow: detect GPS -> reverse geocode -> autofill -> auto-verify
    let lastVerifiedPin = null;
    if (useLocationBtn) {
        useLocationBtn.addEventListener('click', function() {
            if (!('geolocation' in navigator)) { showToast('Geolocation not supported by your browser', 'error'); return; }
            useLocationBtn.disabled = true; const original = useLocationBtn.innerHTML; useLocationBtn.innerHTML = 'Detecting...';
            navigator.geolocation.getCurrentPosition(async pos => {
                try {
                    const lat = pos.coords.latitude, lon = pos.coords.longitude;
                    const apiBase = (window.API_BASE_URL || '').replace(/\/$/, '');
                    const r = await fetch(`${apiBase}/api/geo/reverse?lat=${lat}&lon=${lon}`);
                    if (!r.ok) throw new Error('Reverse geocoding failed');
                    const data = await r.json();
                    if (data && data.pincode) {
                        // Fill state/city if available
                        if (data.state) {
                            for (let i = 0; i < stateInput.options.length; i++) {
                                if (stateInput.options[i].value === data.state) { stateInput.selectedIndex = i; break; }
                            }
                        }
                        if (data.district) { cityInput.value = data.district; }
                        // Fill pincode and auto-verify
                        pincode.value = String(data.pincode).slice(0,6);
                        const ok = await performPincodeVerification(pincode.value);
                        if (ok) {
                            lastVerifiedPin = pincode.value;
                            // Hide Verify button until user edits pincode
                            if (verifyPincodeBtn) verifyPincodeBtn.style.display = 'none';
                            locationNote.innerHTML = 'Location detected and verified';
                            locationNote.style.color = '#4CAF50';
                        }
                        showToast('Location detected successfully!', 'success');
                    } else {
                        showToast('Could not detect pincode from your location', 'error');
                    }
                } catch (err) {
                    console.error('Use Location error:', err);
                    showToast('Error detecting location', 'error');
                } finally {
                    useLocationBtn.disabled = false; useLocationBtn.innerHTML = original;
                }
            }, err => {
                console.error('Geolocation error:', err);
                showToast('Please allow location access', 'error');
                useLocationBtn.disabled = false; useLocationBtn.innerHTML = 'Use Location';
            });
        });
    }

    // If user edits/clears pincode after auto verification, show Verify button back
    pincode.addEventListener('input', function(){
        const val = (this.value || '').trim();
        if (!/^\d{6}$/.test(val) || (lastVerifiedPin && val !== lastVerifiedPin)) {
            isPincodeVerified = false;
            if (verifyPincodeBtn) verifyPincodeBtn.style.display = '';
            // Reset visuals to pending state
            this.style.borderColor = val.length === 6 ? '#ff9800' : '#ff5252';
            this.style.boxShadow = val.length === 6 ? '0 0 5px rgba(255, 152, 0, 0.5)' : '0 0 5px rgba(255, 82, 82, 0.5)';
            locationNote.innerHTML = val.length === 6 ? 'Please click "Verify" button to verify pincode' : 'Please enter a valid 6-digit pincode';
            locationNote.style.color = val.length === 6 ? '#ff9800' : '#ff5252';
            // Clear city/state if user is actively changing
            if (val.length < 6) { cityInput.value = ''; stateInput.selectedIndex = 0; }
        }
    });
    
    // Function to show toast message
    function showToast(message, type = 'success') {
        const toast = document.createElement('div');
        toast.className = `toast ${type}`;
        toast.textContent = String(message ?? '');
        document.body.appendChild(toast);

        toast.style.position = 'fixed';
        toast.style.bottom = '20px';
        toast.style.left = '50%';
        toast.style.transform = 'translateX(-50%)';
        toast.style.padding = '12px 24px';
        toast.style.borderRadius = '8px';
        toast.style.backgroundColor = type === 'success' ? '#4CAF50' : '#f44336';
        toast.style.color = 'white';
        toast.style.zIndex = '1000';
        toast.style.animation = 'fadeInOut 5s forwards';
        toast.style.maxWidth = '80%';
        toast.style.maxHeight = '80vh';
        toast.style.overflowY = 'auto';
        toast.style.textAlign = 'left';
        toast.style.lineHeight = '1.5';
        
        // Add the animation style if not already in the document
        if (!document.getElementById('toast-animation-style')) {
            const style = document.createElement('style');
            style.id = 'toast-animation-style';
            style.textContent = `
                @keyframes fadeInOut {
                    0% { opacity: 0; transform: translate(-50%, 20px); }
                    10% { opacity: 1; transform: translate(-50%, 0); }
                    90% { opacity: 1; transform: translate(-50%, 0); }
                    100% { opacity: 0; transform: translate(-50%, -20px); }
                }
            `;
            document.head.appendChild(style);
        }

        // Use longer duration for error messages, especially for vehicle registration errors
        // If there are multiple lines (contains <br>), use even longer duration
        const hasMultipleErrors = message.includes('<br>');
        const duration = type === 'error' ? (hasMultipleErrors ? 10000 : 6000) : 3000;
        
        setTimeout(() => {
            if (document.body.contains(toast)) {
                document.body.removeChild(toast);
            }
        }, duration);
    }

    // Photo Upload Implementation
    function updatePhotoCounter() {
        const uploadCount = Object.values(uploadedPhotos).filter(photo => photo !== null).length;
        photoCounter.textContent = `${uploadCount}/4 photos uploaded`;
        
        if (uploadCount === 4) {
            photoCounter.style.color = '#4CAF50';
        } else {
            photoCounter.style.color = '#555';
        }
    }
    
    function showError(message) {
        photoError.textContent = message;
        photoError.style.display = 'block';
        
        // Hide error after 5 seconds
        setTimeout(() => {
            photoError.style.display = 'none';
        }, 5000);
    }
    
    function openUploadModal(view) {
        currentUploadView = view;
        
        // Set modal title based on view
        let viewTitle = '';
        switch(view) {
            case 'front': viewTitle = 'Front View'; break;
            case 'side': viewTitle = 'Side View'; break;
            case 'back': viewTitle = 'Back View'; break;
            case 'loading': viewTitle = 'Loading Area'; break;
        }
        
        uploadViewTitle.textContent = `Upload ${viewTitle} Photo`;
        photoUploadModal.style.display = 'flex';
    }
    
    function closeUploadModal() {
        photoUploadModal.style.display = 'none';
        currentUploadView = null;
    }
    
    function getInputForView(view) {
        switch(view) {
            case 'front': return frontViewPhoto;
            case 'side': return sideViewPhoto;
            case 'back': return backViewPhoto;
            case 'loading': return loadingViewPhoto;
        }
    }
    
    function validateImage(file) {
        // Check if it's an image
            if (!file.type.startsWith('image/')) {
            showError(`Selected file is not an image. Please upload only images.`);
            return false;
            }

        // Check file size (max 5MB)
            if (file.size > 5 * 1024 * 1024) {
            showError(`Image size exceeds 5MB. Please upload a smaller image.`);
            return false;
        }
        
        return true;
    }
    
    function handleFileUpload(view, file) {
        if (!validateImage(file)) {
            return;
        }

        const reader = new FileReader();
        const uploadBox = document.querySelector(`.photo-upload-box[data-view="${view}"]`);
        const preview = uploadBox.querySelector('.photo-preview');
        const removeBtn = uploadBox.querySelector('.photo-remove-btn');
        const uploadBtn = uploadBox.querySelector('.photo-upload-btn');
        
        reader.onload = function(e) {
            // Create image and add to preview
            preview.innerHTML = `<img src="${e.target.result}" alt="${view} view">`;
            
            // Store the file
            uploadedPhotos[view] = file;
            console.log(`Photo uploaded for ${view} view:`, file.name);
            console.log('Current uploadedPhotos state:', Object.keys(uploadedPhotos).map(k => ({view: k, hasPhoto: !!uploadedPhotos[k]})));
            
            // Update UI
            uploadBox.classList.add('has-image');
            removeBtn.style.display = 'flex';
            
            // Change upload button text to "Change"
            uploadBtn.innerHTML = '<i class="fas fa-camera"></i> Change';
            
            // Update counter
            updatePhotoCounter();
        };
        
        reader.readAsDataURL(file);
    }
    
    function removePhoto(view) {
        const uploadBox = document.querySelector(`.photo-upload-box[data-view="${view}"]`);
        const preview = uploadBox.querySelector('.photo-preview');
        const removeBtn = uploadBox.querySelector('.photo-remove-btn');
        const uploadBtn = uploadBox.querySelector('.photo-upload-btn');
        const input = getInputForView(view);
        
        // Clear UI
        preview.innerHTML = '';
        uploadBox.classList.remove('has-image');
        removeBtn.style.display = 'none';
        
        // Reset upload button text
        uploadBtn.innerHTML = '<i class="fas fa-camera"></i> Upload';
        
        // Clear file input
        input.value = '';
        
        // Clear stored file
        uploadedPhotos[view] = null;
        console.log(`Removed photo for ${view} view`);
        console.log('Current uploadedPhotos state:', Object.keys(uploadedPhotos).map(k => ({view: k, hasPhoto: !!uploadedPhotos[k]})));
        
        // Update counter
        updatePhotoCounter();
    }
    
    // Initialize photo upload buttons
    photoUploadBoxes.forEach(box => {
        const view = box.dataset.view;
        const uploadBtn = box.querySelector('.photo-upload-btn');
        const removeBtn = box.querySelector('.photo-remove-btn');
        
        // Set up upload button
        uploadBtn.addEventListener('click', () => {
            openUploadModal(view);
        });
        
        // Set up remove button
        removeBtn.addEventListener('click', () => {
            removePhoto(view);
        });
    });
    
    // Modal close button
    closeModalBtn.addEventListener('click', closeUploadModal);
    
    // Handle modal background click to close
    photoUploadModal.addEventListener('click', (e) => {
        if (e.target === photoUploadModal) {
            closeUploadModal();
        }
    });
    
    // Camera option click
    cameraOption.addEventListener('click', () => {
        if (!currentUploadView) return;
        
        const input = getInputForView(currentUploadView);
        input.setAttribute('capture', 'environment');
        input.click();
        closeUploadModal();
    });
    
    // Gallery option click
    galleryOption.addEventListener('click', () => {
        if (!currentUploadView) return;
        
        const input = getInputForView(currentUploadView);
        input.removeAttribute('capture');
        input.click();
        closeUploadModal();
    });
    
    // Handle file input change
    [frontViewPhoto, sideViewPhoto, backViewPhoto, loadingViewPhoto].forEach(input => {
        input.addEventListener('change', function(e) {
            if (this.files && this.files[0]) {
                const view = this.id.replace('ViewPhoto', '').toLowerCase();
                handleFileUpload(view, this.files[0]);
            }
        });
    });
    
    // Handle vehicle number input validation
    vehicleNumberField.addEventListener('input', function() {
        // Convert to uppercase
        this.value = this.value.toUpperCase();
        
        // Auto-format as user types
        autoFormatVehicleNumber(this);
        
        // Validate the format
        validateVehicleNumber(this);
    });
    
    vehicleNumberField.addEventListener('blur', function() {
        // Validate on blur to show error if needed
        validateVehicleNumber(this, true);
    });
    
    // Function to auto-format vehicle number as user types
    function autoFormatVehicleNumber(input) {
        // Remove any non-alphanumeric characters except hyphens
        let value = input.value.replace(/[^A-Z0-9-]/g, '');
        
        // Remove extra hyphens
        value = value.replace(/-+/g, '-');
        
        // Remove hyphen from the beginning if exists
        value = value.replace(/^-/, '');
        
        // Process the input to add hyphens automatically
        let formattedValue = '';
        let rawValue = value.replace(/-/g, ''); // Remove all hyphens
        
        // Add state code (first 2 characters)
        if (rawValue.length > 0) {
            formattedValue += rawValue.substring(0, Math.min(2, rawValue.length));
            
            // Add hyphen after state code if we have more characters
            if (rawValue.length > 2) {
                formattedValue += '-';
            }
        }
        
        // Add district code (next 2 characters)
        if (rawValue.length > 2) {
            formattedValue += rawValue.substring(2, Math.min(4, rawValue.length));
            
            // Add hyphen after district code if we have more characters
            if (rawValue.length > 4) {
                formattedValue += '-';
            }
        }
        
        // Add series (next 2 characters)
        if (rawValue.length > 4) {
            formattedValue += rawValue.substring(4, Math.min(6, rawValue.length));
            
            // Add hyphen after series if we have more characters
            if (rawValue.length > 6) {
                formattedValue += '-';
            }
        }
        
        // Add number (last 4 characters)
        if (rawValue.length > 6) {
            formattedValue += rawValue.substring(6, Math.min(10, rawValue.length));
        }
        
        // Update the input value with formatted value
        input.value = formattedValue;
    }
    
    // Function to validate vehicle number format
    function validateVehicleNumber(input, showError = false) {
        // Remove any existing error message
        const errorMsgId = 'vehicleNumberError';
        const existingError = document.getElementById(errorMsgId);
        if (existingError) {
            existingError.remove();
        }
        
        // Skip validation if manual cart is selected
        if (isManualCartSelected) {
            return true;
        }
        
        // Skip validation if empty (will be caught by required attribute)
        if (!input.value.trim()) {
            input.style.borderColor = '';
            input.style.boxShadow = '';
            return false;
        }
        
        // Regex for vehicle number format: [STATE CODE]-[DISTRICT CODE]-[SERIES]-[NUMBER]
        // STATE CODE: 2 letters, DISTRICT CODE: 2 digits, SERIES: 2 letters, NUMBER: 4 digits
        const vehicleNumberRegex = /^[A-Z]{2}-[0-9]{2}-[A-Z]{2}-[0-9]{4}$/;
        
        if (vehicleNumberRegex.test(input.value)) {
            // Valid format
            input.style.borderColor = '#4CAF50';
            input.style.boxShadow = '0 0 5px rgba(76, 175, 80, 0.5)';
            return true;
        } else {
            // Invalid format
            input.style.borderColor = '#ff5252';
            input.style.boxShadow = '0 0 5px rgba(255, 82, 82, 0.5)';
            
            if (showError && input.value.length > 0) {
                // Create error message element
                const errorMsg = document.createElement('div');
                errorMsg.id = errorMsgId;
                errorMsg.className = 'error-message';
                errorMsg.textContent = 'Invalid vehicle number format';
                input.parentNode.appendChild(errorMsg);
                
                // Show toast with error only if the input is not empty
                if (input.value.length > 0) {
                    showToast('Please enter a valid vehicle registration number', 'error');
                }
            }
            
            return false;
        }
    }
    
    // Form submission handler
    form.addEventListener('submit', function(e) {
        e.preventDefault();

        // Check if user is logged in
        const isLoggedIn = localStorage.getItem('isLoggedIn') === 'true';
        if (!isLoggedIn) {
            alert('Please login to register your vehicle');
            window.location.href = 'login';
            return;
        }
        
        // Validate form
        let isValid = true;
        let errorMessages = [];

        // Check required fields
        const requiredFields = form.querySelectorAll('[required]');
        requiredFields.forEach(field => {
            if (!field.value) {
                isValid = false;
                field.classList.add('error');
                
                // Get field label for error message
                let fieldLabel = field.previousElementSibling ? field.previousElementSibling.textContent.trim() : field.name;
                fieldLabel = fieldLabel.replace(' *', '');
                errorMessages.push(`${fieldLabel} is required`);
            } else {
                field.classList.remove('error');
            }
        });
        
        // Validate contact number length
        if (contactNo.value.length < 10) {
            isValid = false;
            contactNo.classList.add('error');
            errorMessages.push('Contact number must be 10 digits');
        }
        
        // Validate WhatsApp number length only if provided
        if (whatsappNo.value.length > 0 && whatsappNo.value.length < 10) {
            isValid = false;
            whatsappNo.classList.add('error');
            errorMessages.push('WhatsApp number must be 10 digits if provided');
        } else {
            whatsappNo.classList.remove('error');
        }
        
        // Validate pincode verification
        if (!isPincodeVerified) {
            isValid = false;
            pincode.classList.add('error');
            errorMessages.push('Please verify your pincode before submitting');
        }
        
        // Validate vehicle type selection
        const vehicleTypeInputs = document.querySelectorAll('input[name="vehicleType"]');
        let vehicleTypeSelected = false;
        vehicleTypeInputs.forEach(input => {
            if (input.checked) {
                vehicleTypeSelected = true;
            }
        });
        
        if (!vehicleTypeSelected) {
            isValid = false;
            errorMessages.push('Please select a vehicle type');
            // Highlight the vehicle selection button
            vehicleSelectBtn.style.borderColor = '#d9534f';
            vehicleSelectBtn.style.boxShadow = '0 0 5px rgba(217, 83, 79, 0.5)';
            vehicleSelectBtn.classList.add('error');
        } else {
            vehicleSelectBtn.style.borderColor = '';
            vehicleSelectBtn.style.boxShadow = '';
            vehicleSelectBtn.classList.remove('error');
        }
        
        // Validate photos (count must be 4)
        const photoViews = ['front', 'side', 'back', 'loading'];
        const uploadedPhotoCount = photoViews.filter(view => uploadedPhotos[view] !== null).length;
        
        // Debug the photo validation issue
        console.log('Current uploadedPhotos object:', uploadedPhotos);
        console.log('Uploaded photo count:', uploadedPhotoCount);
        console.log('Photo validation details:', photoViews.map(view => ({
            view: view,
            uploaded: !!uploadedPhotos[view],
            fileName: uploadedPhotos[view] ? uploadedPhotos[view].name : 'none'
        })));
        
        // Check if all photos are uploaded
        if (uploadedPhotoCount < 4) {
            isValid = false;
            const missingViews = photoViews.filter(view => !uploadedPhotos[view])
                .map(view => {
                    switch(view) {
                        case 'front': return 'Front View';
                        case 'side': return 'Side View';
                        case 'back': return 'Back View';
                        case 'loading': return 'Loading Area';
                    }
                });
                
            // Highlight missing photo boxes with red border
            missingViews.forEach((viewName, index) => {
                const view = photoViews.filter(v => !uploadedPhotos[v])[index];
                const uploadBox = document.querySelector(`.photo-upload-box[data-view="${view}"]`);
                uploadBox.style.border = '2px solid #d9534f';
                uploadBox.style.animation = 'shake 0.5s';
                
                // Reset after animation
                setTimeout(() => {
                    uploadBox.style.animation = '';
                }, 500);
            });
            
            errorMessages.push(`Please upload all 4 required photos. Missing: ${missingViews.join(', ')}`);
            
            // Update photo counter to show error
            photoCounter.style.color = '#d9534f';
            photoCounter.style.fontWeight = 'bold';
        } else {
            // Reset photo counter style
            photoCounter.style.color = '#4CAF50';
            photoCounter.style.fontWeight = 'normal';
            
            // Reset all photo box borders
            photoViews.forEach(view => {
                const uploadBox = document.querySelector(`.photo-upload-box[data-view="${view}"]`);
                uploadBox.style.border = '';
            });
        }
        
        // Validate terms
        const termsCheckbox = document.getElementById('terms');
        const privacyCheckbox = document.getElementById('privacy');
        
        if (!termsCheckbox.checked) {
            isValid = false;
            errorMessages.push('Please agree to the Terms & Conditions');
            termsCheckbox.parentNode.style.color = '#d9534f';
            termsCheckbox.parentNode.style.fontWeight = 'bold';
        } else {
            termsCheckbox.parentNode.style.color = '';
            termsCheckbox.parentNode.style.fontWeight = '';
        }
        
        if (!privacyCheckbox.checked) {
            isValid = false;
            errorMessages.push('Please agree to the Privacy Policy');
            privacyCheckbox.parentNode.style.color = '#d9534f';
            privacyCheckbox.parentNode.style.fontWeight = 'bold';
        } else {
            privacyCheckbox.parentNode.style.color = '';
            privacyCheckbox.parentNode.style.fontWeight = '';
        }

        // Validate vehicle number format if not a manual cart
        if (!isManualCartSelected && vehicleNumberField.value) {
            if (!validateVehicleNumber(vehicleNumberField, false)) {
                isValid = false;
                errorMessages.push('Invalid vehicle number format');
            }
        }

        if (!isValid) {
            // Display all error messages in a single toast
            showToast(errorMessages.join('<br>'), 'error');
            return;
        }

        // Show loading overlay
        loadingOverlay.style.display = 'flex';
        
        // Get user phone from localStorage
        const userPhone = localStorage.getItem('userPhone');
        
        // Check if user has reached vehicle limit before proceeding
        checkVehicleLimit(userPhone)
            .then(limitData => {
                if (limitData.hasReachedLimit) {
                    // Hide loading overlay
                    loadingOverlay.style.display = 'none';
                    
                    // Show limit reached modal
                    showLimitReachedModal(limitData);
                } else {
                    // Proceed with registration if limit not reached
                    submitVehicleRegistration();
                }
            })
            .catch(error => {
                console.error('Error checking vehicle limit:', error);
                // Proceed anyway in case of error to not block user
                submitVehicleRegistration();
            });
    });
    
    // Function to check vehicle registration limit
    function checkVehicleLimit(contactNumber) {
        const base = window.API_BASE_URL || 'http://localhost:8080';
        return fetch(`${base}/api/users/${contactNumber}/check-vehicle-limit`)
            .then(response => {
                if (!response.ok) {
                    throw new Error('Failed to check vehicle limit');
                }
                return response.json();
            });
    }
    
    // Function to show limit reached modal
    function showLimitReachedModal(limitData) {
        const modal = document.createElement('div');
        modal.className = 'limit-modal';
        
        const modalContent = document.createElement('div');
        modalContent.className = 'limit-modal-content';
        
        const membership = (limitData && limitData.membership) ? String(limitData.membership) : 'Standard';
        const isPremium = membership === 'Premium';
        const membershipEsc = escapeHtml(membership);
        const vehicleCountEsc = escapeHtml((limitData && limitData.vehicleCount != null) ? limitData.vehicleCount : '');
        const maxVehiclesEsc = escapeHtml((limitData && limitData.maxVehicles != null) ? limitData.maxVehicles : '');
        
        modalContent.innerHTML = `
            <div class="limit-modal-header">
                <h3>Vehicle Limit Reached</h3>
                <span class="close-limit-modal">&times;</span>
            </div>
            <div class="limit-modal-body">
                <div class="limit-icon">
                    <i class="fas fa-exclamation-circle"></i>
                </div>
                <p>You have already registered <strong>${vehicleCountEsc}</strong> vehicles with your ${membershipEsc} account, which is the maximum limit.</p>
                
                ${isPremium ? 
                    `<p>As a Premium member, you can register up to ${maxVehiclesEsc} vehicles. To register more vehicles, please use a different phone number to create a new account.</p>` : 
                    `<p>You can upgrade to Premium to register up to 5 vehicles, or use a different phone number to create a new account.</p>
                    <button class="upgrade-button">Upgrade to Premium</button>`
                }
                
                <button class="new-account-button">Use Different Number</button>
                <button class="cancel-button">Cancel</button>
            </div>
        `;
        
        modal.appendChild(modalContent);
        document.body.appendChild(modal);
        
        // Add styles for the modal
        const style = document.createElement('style');
        style.textContent = `
            .limit-modal {
                position: fixed;
                top: 0;
                left: 0;
                width: 100%;
                height: 100%;
                background-color: rgba(0, 0, 0, 0.5);
                display: flex;
                justify-content: center;
                align-items: center;
                z-index: 2000;
            }
            
            .limit-modal-content {
                background-color: white;
                border-radius: 10px;
                max-width: 500px;
                width: 90%;
                box-shadow: 0 5px 15px rgba(0, 0, 0, 0.3);
                animation: modalFadeIn 0.3s;
            }
            
            @keyframes modalFadeIn {
                from { opacity: 0; transform: translateY(-20px); }
                to { opacity: 1; transform: translateY(0); }
            }
            
            .limit-modal-header {
                padding: 15px 20px;
                border-bottom: 1px solid #eee;
                display: flex;
                justify-content: space-between;
                align-items: center;
            }
            
            .limit-modal-header h3 {
                margin: 0;
                color: #333;
            }
            
            .close-limit-modal {
                font-size: 24px;
                cursor: pointer;
                color: #777;
            }
            
            .limit-modal-body {
                padding: 20px;
                text-align: center;
            }
            
            .limit-icon {
                font-size: 60px;
                color: #ff9800;
                margin-bottom: 20px;
            }
            
            .limit-modal-body p {
                margin-bottom: 20px;
                color: #555;
                font-size: 16px;
                line-height: 1.5;
            }
            
            .upgrade-button, .new-account-button, .cancel-button {
                padding: 10px 20px;
                margin: 10px;
                border-radius: 5px;
                border: none;
                cursor: pointer;
                font-size: 16px;
                transition: all 0.3s;
            }
            
            .upgrade-button {
                background-color: #4CAF50;
                color: white;
            }
            
            .new-account-button {
                background-color: #2196F3;
                color: white;
            }
            
            .cancel-button {
                background-color: #f1f1f1;
                color: #555;
            }
            
            .upgrade-button:hover, .new-account-button:hover {
                opacity: 0.9;
                transform: scale(1.05);
            }
            
            .cancel-button:hover {
                background-color: #e0e0e0;
            }
        `;
        document.head.appendChild(style);
        
        // Event listeners
        const closeBtn = modal.querySelector('.close-limit-modal');
        const cancelBtn = modal.querySelector('.cancel-button');
        const newAccountBtn = modal.querySelector('.new-account-button');
        const upgradeBtn = modal.querySelector('.upgrade-button');
        
        const closeModal = () => {
            document.body.removeChild(modal);
            document.head.removeChild(style);
        };
        
        closeBtn.addEventListener('click', closeModal);
        cancelBtn.addEventListener('click', closeModal);
        
        newAccountBtn.addEventListener('click', () => {
            // Show confirmation modal before logging out
            showLogoutConfirmationModal();
        });
        
        if (upgradeBtn) {
            upgradeBtn.addEventListener('click', () => {
                // Redirect to upgrade page (this would be implemented in the future)
                alert('Premium upgrade feature coming soon!');
                closeModal();
            });
        }
    }
    
    // Function to show logout confirmation modal
    function showLogoutConfirmationModal() {
        const currentPhone = localStorage.getItem('userPhone');
        const currentPhoneEsc = escapeHtml(currentPhone);
        
        const modal = document.createElement('div');
        modal.className = 'logout-confirm-modal';
        
        const modalContent = document.createElement('div');
        modalContent.className = 'logout-confirm-content';
        
        modalContent.innerHTML = `
            <div class="logout-confirm-header">
                <h3>Logout Confirmation</h3>
                <span class="close-logout-modal">&times;</span>
            </div>
            <div class="logout-confirm-body">
                <div class="warning-icon">
                    <i class="fas fa-exclamation-triangle"></i>
                </div>
                <p>Now You are going to logout from your account</p>
                <p>If you click on "Yes", you will logout from your account and you will be redirected to the login page where you can login with a new number.</p>
                <p>If you want to login back to your account, please remember your phone number: <strong>${currentPhoneEsc}</strong></p>
                <div class="button-group">
                    <button class="confirm-logout-btn">Yes, Logout</button>
                    <button class="cancel-logout-btn">No, Go back</button>
                </div>
            </div>
        `;
        
        modal.appendChild(modalContent);
        document.body.appendChild(modal);
        
        // Add styles for the modal
        const style = document.createElement('style');
        style.id = 'logout-confirm-style';
        style.textContent = `
            .logout-confirm-modal {
                position: fixed;
                top: 0;
                left: 0;
                width: 100%;
                height: 100%;
                background-color: rgba(0, 0, 0, 0.6);
                display: flex;
                justify-content: center;
                align-items: center;
                z-index: 2100;
            }
            
            .logout-confirm-content {
                background-color: white;
                border-radius: 10px;
                max-width: 500px;
                width: 90%;
                box-shadow: 0 5px 25px rgba(0, 0, 0, 0.4);
                animation: confirmModalIn 0.3s ease-out;
            }
            
            @keyframes confirmModalIn {
                from { opacity: 0; transform: scale(0.8); }
                to { opacity: 1; transform: scale(1); }
            }
            
            .logout-confirm-header {
                padding: 15px 20px;
                border-bottom: 1px solid #eee;
                display: flex;
                justify-content: space-between;
                align-items: center;
                background-color: #f8f9fa;
                border-radius: 10px 10px 0 0;
            }
            
            .logout-confirm-header h3 {
                margin: 0;
                color: #333;
                font-weight: bold;
            }
            
            .close-logout-modal {
                font-size: 24px;
                cursor: pointer;
                color: #777;
                transition: color 0.2s;
            }
            
            .close-logout-modal:hover {
                color: #333;
            }
            
            .logout-confirm-body {
                padding: 25px;
                text-align: center;
            }
            
            .warning-icon {
                font-size: 64px;
                color: #ff9800;
                margin-bottom: 20px;
                animation: pulse 2s infinite;
            }
            
            .logout-confirm-body p {
                margin-bottom: 15px;
                color: #333;
                font-size: 16px;
                line-height: 1.6;
            }
            
            .logout-confirm-body p:first-of-type {
                font-size: 18px;
                font-weight: bold;
                color: #e53935;
            }
            
            .button-group {
                display: flex;
                justify-content: center;
                margin-top: 25px;
            }
            
            .confirm-logout-btn, .cancel-logout-btn {
                padding: 12px 24px;
                margin: 0 10px;
                border-radius: 5px;
                border: none;
                cursor: pointer;
                font-size: 16px;
                font-weight: bold;
                transition: all 0.3s;
            }
            
            .confirm-logout-btn {
                background-color: #e53935;
                color: white;
            }
            
            .cancel-logout-btn {
                background-color: #f1f1f1;
                color: #333;
            }
            
            .confirm-logout-btn:hover {
                background-color: #c62828;
                transform: translateY(-2px);
                box-shadow: 0 3px 10px rgba(0, 0, 0, 0.2);
            }
            
            .cancel-logout-btn:hover {
                background-color: #e0e0e0;
                transform: translateY(-2px);
            }
        `;
        document.head.appendChild(style);
        
        // Event listeners
        const closeBtn = modal.querySelector('.close-logout-modal');
        const cancelBtn = modal.querySelector('.cancel-logout-btn');
        const confirmBtn = modal.querySelector('.confirm-logout-btn');
        
        const closeConfirmModal = () => {
            document.body.removeChild(modal);
            document.head.removeChild(style);
        };
        
        closeBtn.addEventListener('click', closeConfirmModal);
        cancelBtn.addEventListener('click', closeConfirmModal);
        
        confirmBtn.addEventListener('click', () => {
            // Logout and redirect to login
            localStorage.removeItem('isLoggedIn');
            localStorage.removeItem('userPhone');
            window.location.href = 'login';
        });
    }
    
    // Function to add newly registered vehicle to localStorage
    function addRegisteredVehicle(vehicleNumber) {
        const registeredVehicles = JSON.parse(localStorage.getItem('registeredVehicles') || '[]');
        if (!registeredVehicles.includes(vehicleNumber)) {
            registeredVehicles.push(vehicleNumber);
            localStorage.setItem('registeredVehicles', JSON.stringify(registeredVehicles));
        }
    }
    
    // Function to submit vehicle registration
    function submitVehicleRegistration() {
        // Check if pincode has been verified
        if (!isPincodeVerified) {
            showToast('Please verify your pincode first to get city and state details', 'error');
            pincode.focus();
            return;
        }
        
        // Create FormData object to send form data including files
        const formData = new FormData();
        
        // Add text fields
        formData.append('fullName', document.getElementById('name').value);
        
        // Get selected vehicle type
        const vehicleTypeInputs = document.querySelectorAll('input[name="vehicleType"]');
        let selectedVehicleType = "";
        vehicleTypeInputs.forEach(input => {
            if (input.checked) {
                selectedVehicleType = input.value;
                formData.append('vehicleType', input.value);
            }
        });
        
        // Get vehicle number
        const vehicleNumber = document.getElementById('vehicleNumber').value;
        
        // Add user phone from localStorage
        const userPhone = localStorage.getItem('userPhone');
        formData.append('contactNumber', userPhone || contactNo.value);
        
        // Only append WhatsApp number if it's not empty (it's optional)
        if (whatsappNo.value.trim() !== '') {
            formData.append('whatsappNumber', whatsappNo.value);
        }
        
        // Only append vehicle number if not a manual cart
        if (!isManualCartSelected) {
            formData.append('vehiclePlateNumber', vehicleNumber);
        } else {
            // For manual carts, add a placeholder value since the backend requires this field
            formData.append('vehiclePlateNumber', 'MANUAL-CART-' + Date.now());
        }
        
        formData.append('state', document.getElementById('state').value);
        formData.append('city', document.getElementById('city').value);
        formData.append('pincode', pincode.value);
        
        // Add images
        let photoCount = 0;
        Object.keys(uploadedPhotos).forEach(view => {
            if (uploadedPhotos[view]) {
                formData.append('vehicleImages', uploadedPhotos[view]);
                console.log(`Adding ${view} photo to form submission:`, uploadedPhotos[view].name);
                photoCount++;
            }
        });
        
        console.log(`Submitting form with ${photoCount} photos`);
        
        // Extra validation check before submission
        if (photoCount < 4) {
            console.error("Not all photos were included in the form submission!");
            showToast("Error: Failed to include all photos. Please try again.", "error");
            loadingOverlay.style.display = 'none';
            return;
        }
        
        // Show loading overlay before submission
        loadingOverlay.style.display = 'flex';
        
        // Send data to backend
        const base = window.API_BASE_URL || 'http://localhost:8080';
        fetch(`${base}/api/registration`, {
            method: 'POST',
            body: formData
        })
        .then(response => response.json())
        .then(data => {
            // Hide loading overlay
            loadingOverlay.style.display = 'none';
            
            if (data.success) {
                // Add vehicle to registered vehicles in localStorage
                if (!isManualCartSelected) {
                addRegisteredVehicle(vehicleNumber);
                } else {
                    // For manual carts, use the vehicle type as identifier
                    addRegisteredVehicle("Manual Cart");
                }
                
                // Show celebration overlay
                celebrationOverlay.style.display = 'flex';
                
                // Clear form
                form.reset();
                
                // Clear photo uploads
                Object.keys(uploadedPhotos).forEach(view => {
                    removePhoto(view);
                });
                
                // Reset vehicle type button
                vehicleSelectBtn.querySelector('span').textContent = 'Select Vehicle Type';
                
                // After 3 seconds, redirect to index
                setTimeout(() => {
                    window.location.href = 'index';
                }, 3000);
            } else {
                showToast('Error: ' + (data.message || 'Registration failed'), 'error');
            }
        })
        .catch(error => {
            // Hide loading overlay
            loadingOverlay.style.display = 'none';
            console.error('Error:', error);
            showToast('An error occurred. Please try again.', 'error');
        });
    }
});
