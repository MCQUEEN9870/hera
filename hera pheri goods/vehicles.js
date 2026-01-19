document.addEventListener('DOMContentLoaded', function() {
    // DOM Elements
    const vehicleSearchForm = document.getElementById('vehicleSearchForm');
    const vehicleCategorySelect = document.getElementById('vehicleCategory');
    const vehicleTypeSelect = document.getElementById('vehicleType');
    const stateSelect = document.getElementById('state');
    // City input no longer exists; use district select if present
    const cityInput = document.getElementById('city');
    const districtSelect = document.getElementById('district');
    const pincodeInput = document.getElementById('pincode');
    // pincodeMeta and verify button removed per latest UX
    const vehiclesGrid = document.getElementById('vehiclesGrid');
    const resultsCount = document.getElementById('resultsCount');
    const noResults = document.getElementById('noResults');
    const loadingOverlay = document.getElementById('loadingOverlay');
    // Sort dropdown removed from UI; backend handles ordering (premium-first oldest-first)
    const sortSelect = null;
    const vehicleModal = document.getElementById('vehicleModal');
    const modalClose = document.getElementById('modalClose');
    const modalHeader = document.getElementById('modalHeader');
    const modalGallery = document.getElementById('modalGallery');
    const modalInfo = document.getElementById('modalInfo');
    const contactDriverBtn = document.getElementById('contactDriverBtn');
    const imageLightbox = document.getElementById('imageLightbox');
    const lightboxImage = document.getElementById('lightboxImage');
    const closeLightbox = document.getElementById('closeLightbox');
    const favoriteBtn = document.getElementById('favoriteBtn');
    
    // API endpoint (normalized to ensure exactly one /api/ suffix)
    function buildApiBase() {
        const raw = window.API_BASE_URL || 'http://localhost:8080';
        try {
            const u = new URL(raw);
            // Normalize trailing slash and ensure single /api/ context
            let path = (u.pathname || '').replace(/\/+$/, '');
            if (!/\/api$/i.test(path)) {
                path = path + '/api';
            }
            u.pathname = path.endsWith('/') ? path : path + '/';
            return u.origin + u.pathname;
        } catch (e) {
            // Fallback string ops if URL parsing fails
            const base = String(raw).replace(/\/+$/, '');
            return base.endsWith('/api') ? base + '/' : base + '/api/';
        }
    }
    const API_BASE_URL = buildApiBase();
    
    // Log the API base URL for debugging
    console.log('Using API base URL:', API_BASE_URL);

    // Basic HTML escaping for any untrusted text inserted via innerHTML
    function escapeHtml(value) {
        const s = (value === null || value === undefined) ? '' : String(value);
        return s
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;')
            .replace(/'/g, '&#39;');
    }

    // Allow only safe URL schemes/paths for <img src> etc.
    function safeUrl(input) {
        const raw = (input === null || input === undefined) ? '' : String(input).trim();
        if (!raw) return '';
        if (raw.startsWith('attached_assets/') || raw.startsWith('./') || raw.startsWith('../') || raw.startsWith('/')) return raw;
        if (/^https?:\/\//i.test(raw)) return raw;
        if (/^data:image\//i.test(raw)) return raw;
        return '';
    }

    // Pagination state (temporary PAGE_SIZE=2 for visual testing; will revert to 20)
    let currentPage = 1;
    const PAGE_SIZE = 20; // Restored standard page size
    let totalPages = 1;
    let totalItems = 0;
    
    // Remove direct Supabase usage in frontend
    const REGISTRATIONS_TABLE = "registration";
    
    // Check if user is logged in
    const isLoggedIn = localStorage.getItem('isLoggedIn') === 'true';
    const authSection = document.getElementById('authSection');
    
    if (isLoggedIn) {
        authSection.style.display = 'block';
    }
    
    // Guided flow controls state enablement; don't force-enable here
    if (cityInput) { try { cityInput.setAttribute('readonly', 'readonly'); } catch(_){} }
    // stateSelect enable/disable is handled by vehicles.html wizard

    // Track whether location was verified by pincode
    let locationVerified = false;
    // Guard to avoid clearing meta when we programmatically set district after verify
    let suppressMetaClear = false;
    window.locationVerified = false;
    // Prevent duplicate auto-verifications
    let isVerifyingPincode = false;
    // Promise for any in-flight verification (allows Search to await silently)
    let currentPincodeVerificationPromise = null;
    // Track the last verified pincode to avoid clearing verification on programmatic or identical input
    let lastVerifiedPin = null;

    // Puducherry UT enclaves: keep these pins local-only (no India Post API)
    const PUDUCHERRY_LOCAL_PIN_TO_DISTRICT = {
        '605001': 'Puducherry',
        '605007': 'Puducherry',
        '605110': 'Puducherry',
        '605008': 'Puducherry',
        '605014': 'Puducherry',
        '609602': 'Karaikal',
        '673310': 'Mahe',
        '533464': 'Yanam'
    };

    function verifyPuducherryPinLocal(pin) {
        const p = String(pin || '').trim();
        const district = PUDUCHERRY_LOCAL_PIN_TO_DISTRICT[p];
        if (!district) return false;
        try {
            if (stateSelect) {
                stateSelect.value = 'Puducherry';
                // Trigger wizard state change so districts populate
                try { stateSelect.dispatchEvent(new Event('change')); } catch(_) {}
            }
            // Mark verified immediately so other handlers don't override
            locationVerified = true;
            window.locationVerified = true;
            lastVerifiedPin = p;

            // Ensure pincode input preserves user's value
            try { if (pincodeInput && (!pincodeInput.value || pincodeInput.value !== p)) { pincodeInput.value = p; } } catch(_) {}

            // Set district after state change
            if (districtSelect) {
                setTimeout(() => {
                    try {
                        districtSelect.disabled = false;
                        const has = Array.from(districtSelect.options).some(o => (o.value || '').toLowerCase() === district.toLowerCase());
                        if (!has) {
                            const opt = document.createElement('option');
                            opt.value = district;
                            opt.textContent = district;
                            districtSelect.appendChild(opt);
                        }
                        districtSelect.value = district;
                        suppressMetaClear = true;
                        districtSelect.dispatchEvent(new Event('change'));
                    } catch(_) {}
                }, 250);
            }

            // Hide any open suggestions and chevron after verification
            try {
                const pinSug = document.getElementById('pincodeSuggestions');
                if (pinSug) { pinSug.style.display = 'none'; pinSug.innerHTML = ''; }
                const chevron = document.getElementById('pincodeChevron');
                if (chevron) chevron.style.display = 'none';
                if (pincodeInput) pincodeInput.placeholder = 'Enter pincode';
            } catch(_) {}

            // Visual verified styling
            try {
                if (pincodeInput) {
                    pincodeInput.classList.add('pin-verified');
                    pincodeInput.style.borderColor = '#10b981';
                    pincodeInput.style.boxShadow = '0 0 0 4px rgba(16,185,129,0.15)';
                }
            } catch(_) {}

            showToast('Pincode verified successfully!', 'success');
            return true;
        } catch (e) {
            return false;
        }
    }
    
    // Parse URL parameters to pre-fill the form
    const urlParams = new URLSearchParams(window.location.search);

    function inferCategoryFromType(typeValue) {
        const t = String(typeValue || '').trim();
        if (!t) return '';
        // Goods
        const goodsTypes = new Set([
            'Manual Cart (Thel / Rickshaw)','Auto Loader (CNG loader)','Tata Ace (Chhota Hathi)','E-Rickshaw Loader (Tuk-Tuk)',
            'Mini Truck (Eicher Canter)','Vikram Tempo','Bolero Pickup (MaXX)','Tata 407','6 wheeler',
            'Container Truck','Open Body Truck (6 wheeler)','Closed Body Truck','Flatbed Truck','8 wheeler','12 wheeler and onwards',
            'JCB','Crane','Trailer Truck','Tipper Truck (Dumper Truck)','Tanker Truck','Garbage Truck',
            'Ambulance','Tow-Truck',
            'Refrigerated Vans','Refrigerated Trucks',
            'Packer&Movers','Parcel Delivery',
            'Food trucks','Beverage trucks'
        ]);
        if (goodsTypes.has(t)) return 'goods';
        // Passenger
        const passengerTypes = new Set([
            'Innova Crysta','Ertiga','Dzire','Sedan (Verna, Ciaz, etc.)','Hatchback (WagonR, i10, etc.)','Other Taxi Cars',
            '26-seater Mini Bus','35-seater AC Bus','45+ Seater Tourist Coach','Volvo Type Bus','Other Bus Types'
        ]);
        if (passengerTypes.has(t)) return 'passenger';
        // Rental
        const rentalTypes = new Set([
            'Fortuner','Audi/BMW/Mercedes','Luxury Vintage Cars','Open Jeep (Decorated)','SUV/MUV for Baraat','Other Rental Vehicles'
        ]);
        if (rentalTypes.has(t)) return 'rental';
        return '';
    }

    // Prefer explicit category param if present
    if (urlParams.has('category') && vehicleCategorySelect) {
        const category = String(urlParams.get('category') || '').trim().toLowerCase();
        if (category === 'goods' || category === 'passenger' || category === 'rental') {
            vehicleCategorySelect.value = category;
            try {
                vehicleCategorySelect.dispatchEvent(new Event('change', { bubbles: true }));
            } catch (_) {}
        }
    }

    if (urlParams.has('type')) {
        const vehicleType = urlParams.get('type');
        console.log('Setting vehicle type from URL:', vehicleType);

        // If category wasn't provided, infer it from the type (back-compat for old links)
        if (vehicleCategorySelect && !vehicleCategorySelect.value) {
            const inferred = inferCategoryFromType(vehicleType);
            if (inferred) {
                vehicleCategorySelect.value = inferred;
                try {
                    vehicleCategorySelect.dispatchEvent(new Event('change', { bubbles: true }));
                } catch (_) {}
            }
        }
        
        // Add a small delay to ensure dropdown is fully loaded
        setTimeout(() => {
            console.log('Available dropdown options:', Array.from(vehicleTypeSelect.options).map(opt => ({value: opt.value, text: opt.text})));
            
            // Try to set the value directly first
            vehicleTypeSelect.value = vehicleType;
            
            // Check if the value was set correctly
            if (vehicleTypeSelect.value !== vehicleType) {
                console.warn('Vehicle type mismatch! URL has:', vehicleType, 'but dropdown shows:', vehicleTypeSelect.value);
                console.log('Dropdown element:', vehicleTypeSelect);
                console.log('Dropdown selectedIndex:', vehicleTypeSelect.selectedIndex);
                
                // Try alternative methods
                console.log('Trying to find option by value...');
                let found = false;
                for (let i = 0; i < vehicleTypeSelect.options.length; i++) {
                    const option = vehicleTypeSelect.options[i];
                    console.log(`Option ${i}: value="${option.value}" text="${option.text}"`);
                    if (option.value === vehicleType) {
                        console.log('Found exact match by value!');
                        vehicleTypeSelect.selectedIndex = i;
                        found = true;
                        break;
                    }
                }
                
                if (!found) {
                    console.log('Trying to find option by text...');
                    for (let i = 0; i < vehicleTypeSelect.options.length; i++) {
                        const option = vehicleTypeSelect.options[i];
                        if (option.text.toLowerCase().includes(vehicleType.toLowerCase().replace('&', ''))) {
                            console.log('Found partial match by text!');
                            vehicleTypeSelect.selectedIndex = i;
                            found = true;
                            break;
                        }
                    }
                }
                
                // Special handling for Packer&Movers
                if (!found && vehicleType === 'Packer&Movers') {
                    console.log('Special handling for Packer&Movers...');
                    for (let i = 0; i < vehicleTypeSelect.options.length; i++) {
                        const option = vehicleTypeSelect.options[i];
                        if (option.value === 'Packer&Movers' || option.text.includes('Packers') || option.text.includes('Movers')) {
                            console.log('Found Packer&Movers by special handling!');
                            vehicleTypeSelect.selectedIndex = i;
                            found = true;
                            break;
                        }
                    }
                }
                
                if (!found) {
                    console.error('Could not find matching option for:', vehicleType);
                }
            } else {
                console.log('Vehicle type set successfully:', vehicleTypeSelect.value);
            }

            // Trigger downstream enablement in vehicles.html wizard
            try {
                vehicleTypeSelect.dispatchEvent(new Event('change', { bubbles: true }));
            } catch (_) {}
        }, 100);
    }
    if (urlParams.has('state') && stateSelect) {
        stateSelect.value = urlParams.get('state');
    }
    // Map old city param to new district if present
    if (urlParams.has('city') && districtSelect) {
        districtSelect.value = urlParams.get('city');
    }
    if (urlParams.has('pincode') && pincodeInput) {
        if ('value' in pincodeInput) pincodeInput.value = urlParams.get('pincode');
    }
    
    // If parameters are present in URL, just prompt user to verify pincode
    if (urlParams.has('type') || urlParams.has('state') || urlParams.has('pincode')) {
        setTimeout(() => {
            showToast('Please verify your pincode to see available vehicles', 'info');
        }, 1000);
    }
    
    // NOTE: Pincode input sanitization + auto-verify + progress-bar updates are handled in vehicles.html.
    // Keep vehicles.js focused on Search + API integration to avoid overlapping verifiers.
    // Also clear meta when district changes (location context changed)
    if (districtSelect) {
        districtSelect.addEventListener('change', function(){
            if (suppressMetaClear) {
                // One-shot skip: we just set district programmatically after verify
                suppressMetaClear = false;
                return;
            }
            locationVerified = false;
            window.locationVerified = false;
        });
    }

    // Verification that returns a promise so Search can await it without a second click.
    async function verifyPincodeAsync(pincodeValue) {
        const pin = String(pincodeValue || '').trim();
        console.log('Verifying pincode:', pin);

        // Puducherry UT enclave pins are handled locally (no API)
        if (verifyPuducherryPinLocal(pin)) {
            return true;
        }

        // If already verified for the same pin, skip
        if (window.locationVerified && lastVerifiedPin && pin === lastVerifiedPin) {
            console.log('Pincode already verified, skipping re-verification');
            return true;
        }

        if (!pin || !/^\d{6}$/.test(pin)) {
            showToast('Please enter a valid 6-digit pincode', 'error');
            return false;
        }

        // If a verification is already in-flight, await it.
        if (currentPincodeVerificationPromise) {
            try { return await currentPincodeVerificationPromise; } catch(_) { return false; }
        }

        isVerifyingPincode = true;
        currentPincodeVerificationPromise = (async () => {
            try {
                const response = await fetch(`https://api.postalpincode.in/pincode/${pin}`);
                console.log('Pincode API response status:', response.status);
                const data = await response.json();
                console.log('Pincode API response:', data);
                return !!handlePincodeResponse(data, pin);
            } catch (error) {
                console.error('Error verifying pincode:', error);
                // Only show error if verification isn't already complete
                if (!window.locationVerified && !locationVerified) {
                    showToast('Error verifying pincode. Please try again.', 'error');
                } else {
                    console.log('Verification already completed, ignoring late error');
                }
                return false;
            } finally {
                isVerifyingPincode = false;
                currentPincodeVerificationPromise = null;
            }
        })();

        return await currentPincodeVerificationPromise;
    }

    // Back-compat: keep old entry point name for any other callers
    function verifyPincode(pincodeValue) {
        void verifyPincodeAsync(pincodeValue);
    }
    
    // Function to handle pincode API response
    function handlePincodeResponse(data, pincodeValue) {
        if (data && data[0].Status === 'Success') {
            const postOffice = data[0].PostOffice[0];
            
            // Auto-fill fields
            // Set state by value instead of index for more reliable behavior
            const stateValue = postOffice.State || postOffice.Circle;
            for (let i = 0; i < stateSelect.options.length; i++) {
                if (stateSelect.options[i].value === stateValue) {
                    stateSelect.selectedIndex = i;
                    break;
                }
            }
            // Mark verified immediately so downstream change handlers don't clear values
            locationVerified = true;
            window.locationVerified = true;
            lastVerifiedPin = pincodeValue;

            // Ensure pincode input preserves user's value
            try { if (pincodeInput && (!pincodeInput.value || pincodeInput.value !== pincodeValue)) { pincodeInput.value = pincodeValue; } } catch(_) {}

            // Trigger state change so district options populate (handled by vehicles.html wizard)
            try { stateSelect.dispatchEvent(new Event('change')); } catch(_) {}
            
            // Set district/city if present in DOM
            const districtName = postOffice.District || postOffice.Division || postOffice.Region || postOffice.Block;
            if (cityInput) cityInput.value = districtName || '';
            if (districtSelect && districtName) {
                // Wait briefly for district list to populate after state change
                setTimeout(() => {
                    try {
                        districtSelect.value = districtName;
                        suppressMetaClear = true; // prevent clearing meta due to our own change
                        districtSelect.dispatchEvent(new Event('change'));
                    } catch(_) {}
                }, 300);
            }
            // Already marked verified above
            
            // Update location note
            const locationNote = document.querySelector('.location-note');
            if (locationNote) {
                locationNote.innerHTML = 'Location verified';
                locationNote.style.color = '#ffffff';
            }

            // Meta line under pincode removed as per request

            // Hide any open suggestions and chevron after verification
            try {
                const pinSug = document.getElementById('pincodeSuggestions');
                if (pinSug) { pinSug.style.display = 'none'; pinSug.innerHTML = ''; }
                const chevron = document.getElementById('pincodeChevron');
                if (chevron) chevron.style.display = 'none';
                if (pincodeInput) pincodeInput.placeholder = 'Enter pincode';
            } catch(_) {}
            
            // Log successful verification
            console.log('Pincode verification successful:', {
                pincode: pincodeValue,
                state: stateValue,
                city: cityInput.value
            });
            
            // Show success message
            showToast('Pincode verified successfully!', 'success');
            // Add green border indicator
            try {
                if (pincodeInput) {
                    pincodeInput.classList.add('pin-verified');
                    // Apply inline styles as a fallback in case CSS specificity blocks the class
                    pincodeInput.style.borderColor = '#10b981';
                    pincodeInput.style.boxShadow = '0 0 0 4px rgba(16,185,129,0.15)';
                }
            } catch(_) {}
            
            // Don't automatically trigger search after verification anymore
            // Let the user click the search button themselves
            return true;
        } else {
            console.log('API response indicates failure or no data:', data);
            // Do not auto-approximate; require correct verification or selection
            if (!window.locationVerified && !locationVerified) {
                showToast('Could not verify pincode. Please recheck or select from dropdown.', 'error');
            }
            return false;
        }
    }
    
    // Manual approximate pincode handling removed by request
    
    // Function to show toast message (use global if available to avoid duplication)
    function showToast(message, type = 'success') {
        if (window.showToast) return window.showToast(message, type);
        const old = document.getElementById('globalToast');
        if (old) old.remove();
        const toast = document.createElement('div');
        toast.id = 'globalToast';
        toast.className = `toast ${type}`;
        // Safely render line breaks for messages joined with "<br>" without using innerHTML
        const raw = String(message ?? '');
        toast.textContent = '';
        const parts = raw.split(/<br\s*\/?>/i);
        parts.forEach((part, idx) => {
            toast.appendChild(document.createTextNode(part));
            if (idx < parts.length - 1) toast.appendChild(document.createElement('br'));
        });
        toast.style.cssText = 'position:fixed;bottom:30px;right:30px;background:#333;color:#fff;padding:14px 20px;border-radius:10px;z-index:10000;';
        document.body.appendChild(toast);
        const duration = type === 'error' ? 6000 : 3000;
        setTimeout(() => { toast.remove(); }, duration);
    }
    
    // Search form submission
    vehicleSearchForm.addEventListener('submit', async function(e) {
        e.preventDefault();
        
        // Get form values
        const vehicleType = vehicleTypeSelect.value;
        const state = stateSelect ? stateSelect.value : '';
        const city = (cityInput ? cityInput.value : (districtSelect ? districtSelect.value : ''));
        const pincode = (pincodeInput && 'value' in pincodeInput) ? pincodeInput.value.trim() : '';
        
        console.log('Form submitted with values:', { vehicleType, state, city, pincode });
        console.log('Form validation starting...');
        
        // Require either verified pincode or state+district path
        const hasStateDistrict = state && city && state !== '' && city !== '';
        const validPin = /^\d{6}$/.test(pincode);
        const globalVerified = !!(window.locationVerified || locationVerified);
        // If a pincode is present but not verified, silently verify and continue (no double-click)
        if (validPin && !globalVerified) {
            // Prefer verifier from vehicles.html (it also auto-fills state/district and updates progress)
            try {
                if (window.__pincodeVerifyPromise && typeof window.__pincodeVerifyPromise.then === 'function') {
                    await window.__pincodeVerifyPromise;
                }
            } catch(_) { /* ignore */ }

            if (!window.locationVerified && typeof window.__verifyPinAndAutofill === 'function') {
                // No toast here; vehicles.html shows toast when user enters 6 digits
                try { await window.__verifyPinAndAutofill(pincode); } catch(_) {}
            }

            const nowVerified = !!(window.locationVerified || locationVerified);
            if (!nowVerified) {
                // Keep user on pincode step; vehicles.html will show any needed guidance
                return;
            }
        }
        // Require pincode selection when state & district chosen but no pincode
        if (!validPin && hasStateDistrict) {
            showToast('Please select your pincode first', 'error');
            try { pincodeInput.focus(); } catch(_) {}
            return;
        }
        // Ensure vehicle type chosen
        if (!vehicleType) { showToast('Select a vehicle type','error'); vehicleTypeSelect.focus(); return; }
        
        console.log('Form validation passed, proceeding with search...');

        // Progress bar: move to Search step once all validations are satisfied
        try { if (typeof window.updateProgressBar === 'function') window.updateProgressBar(6); } catch(_) {}
        
    // Show loading overlay
        loadingOverlay.style.display = 'flex';
        
        // Log search criteria for debugging
        console.log('Search criteria:', { vehicleType, state, city, pincode });
        
        // Fetch vehicles from the database
        try {
            console.log('Calling fetchVehiclesFromDatabase function...');
            fetchVehiclesFromDatabase(vehicleType, state, city, pincode);
        } catch (error) {
            console.error('Error in fetchVehiclesFromDatabase:', error);
            // Hide loading overlay
            loadingOverlay.style.display = 'none';
            // Show error toast
            showToast('Error searching vehicles. Please try again.', 'error');
        }
    });

    // ===== Initial Load: Fetch all vehicles for first paint (SEO + user trust) =====
    // This bypasses form validation so bots & users instantly see real data.
    function initialLoadAllVehicles() {
        try {
            console.log('[InitialLoad] Fetching all vehicles with no filters');
            // Show a subtle loading indicator
            loadingOverlay.style.display = 'flex';
            // Directly call the Spring Boot API without query params
            const base = API_BASE_URL.endsWith('/') ? API_BASE_URL : API_BASE_URL + '/';
            const apiUrl = base + 'vehicles/search?page=' + currentPage + '&size=' + PAGE_SIZE;
            fetch(apiUrl, { headers: { 'Accept': 'application/json' } })
                .then(r => {
                    if (!r.ok) throw new Error('Initial load request failed: ' + r.status);
                    return r.json();
                })
                .then(data => {
                    console.log('[InitialLoad] Raw response:', data);
                    const vehicles = (data && data.vehicles) ? data.vehicles : [];
                    totalPages = data.totalPages || 1;
                    totalItems = data.totalItems || vehicles.length;
                    displayResults(vehicles);
                    updatePaginationControls();
                    // Add lightweight JSON-LD for first 10 vehicles to help crawlers
                    injectStructuredData(vehicles.slice(0, 10));
                })
                .catch(err => {
                    console.error('[InitialLoad] Error:', err);
                    loadingOverlay.style.display = 'none';
                });
        } catch (e) {
            console.error('[InitialLoad] Unexpected error:', e);
            loadingOverlay.style.display = 'none';
        }
    }

    // Inject JSON-LD structured data describing vehicles (helps Google understand real inventory)
    function injectStructuredData(list) {
        try {
            if (!list || !list.length) return;
            // Remove any previous injection to avoid duplicates
            const old = document.getElementById('vehicles-jsonld');
            if (old) old.remove();
            // Helper: choose a primary image URL for a vehicle and make it absolute
            const toAbsolute = (url) => {
                try {
                    // If already absolute, new URL will keep it; otherwise base it on current origin
                    return new URL(url, window.location.origin).href;
                } catch (_) {
                    return url;
                }
            };
            const getPrimaryImage = (v) => {
                let candidates = [];
                if (Array.isArray(v.images) && v.images.length) candidates = candidates.concat(v.images);
                if (Array.isArray(v.vehicleImageUrls) && v.vehicleImageUrls.length) candidates = candidates.concat(v.vehicleImageUrls);
                if (v.vehicle_image_urls_json) {
                    try {
                        const arr = JSON.parse(v.vehicle_image_urls_json);
                        if (Array.isArray(arr)) candidates = candidates.concat(arr);
                    } catch(_) {}
                }
                // Filter unusable placeholders
                candidates = candidates.filter(u => typeof u === 'string' && u.trim() && !u.endsWith('.hidden_folder') && !u.endsWith('.folder'));
                if (candidates.length) {
                    const raw = candidates[0];
                    // Pretty rewrite for Supabase public bucket paths
                    const marker = '/storage/v1/object/public/vehicle-images/';
                    if (raw.includes('supabase.co') && raw.includes(marker)) {
                        try {
                            const after = raw.split(marker)[1]; // e.g., "79/filename.jpg"
                            const pretty = `${window.location.origin}/images/vehicles/${after}`;
                            return pretty;
                        } catch (_) {
                            return toAbsolute(raw);
                        }
                    }
                    return toAbsolute(raw);
                }
                // Fallback to a deterministic default image so the required field is present
                return toAbsolute('attached_assets/images/default-vehicle.png');
            };
            const items = list.map(v => ({
                '@type': 'Product',
                'name': (v.name || (v.type || 'Vehicle') + ' #' + (v.id || '')),
                'productID': String(v.id || ''),
                'category': v.type || v.vehicleType || 'Transport Vehicle',
                'description': (v.description && v.description.trim()) ? v.description : 'Transport service vehicle available for local goods movement',
                // Google Merchant listings treats image as a REQUIRED property
                'image': getPrimaryImage(v),
                // Use Brand object form for clarity
                'brand': { '@type': 'Brand', 'name': 'Herapherigoods' },
                'areaServed': [v.locationCity, v.locationState].filter(Boolean).join(', '),
                'offers': {
                    '@type': 'Offer',
                    'price': '0',
                    'priceCurrency': 'INR',
                    'availability': 'https://schema.org/InStock'
                }
            }));
            const jsonld = {
                '@context': 'https://schema.org',
                '@type': 'ItemList',
                'name': 'Registered Transport Vehicles',
                'itemListElement': items.map((it, idx) => ({ '@type': 'ListItem', position: idx + 1, item: it }))
            };
            const script = document.createElement('script');
            script.type = 'application/ld+json';
            script.id = 'vehicles-jsonld';
            script.textContent = JSON.stringify(jsonld);
            document.head.appendChild(script);
            console.log('[InitialLoad] Injected JSON-LD for', items.length, 'vehicles');
        } catch(e) {
            console.warn('Structured data injection failed:', e);
        }
    }

    // Trigger initial load after a short delay to allow environment setup
    setTimeout(initialLoadAllVehicles, 300);
    
    // Function to fetch vehicles from the database
    function fetchVehiclesFromDatabase(vehicleType, state, city, pincode) {
        // Show loading overlay
        loadingOverlay.style.display = 'flex';
        
        // Build query parameters for API
        const queryParams = new URLSearchParams();
        if (vehicleType && vehicleType !== 'any') queryParams.append('type', vehicleType);
        if (state && state !== 'any') queryParams.append('state', state);
        if (city && city.trim() !== '') queryParams.append('city', city);
        if (pincode && pincode.trim() !== '') queryParams.append('pincode', pincode);
        queryParams.append('page', currentPage);
        queryParams.append('size', PAGE_SIZE);
        lastSearchCriteria = { vehicleType, state, city, pincode };
        
        // Use Spring Boot API
        console.log('Using Spring Boot API for vehicle search...');
        trySpringBootAPI(vehicleType, state, city, pincode, queryParams);
    }
    
    // Function to try Spring Boot API first
    function trySpringBootAPI(vehicleType, state, city, pincode, queryParams) {
        // Build API URL with proper formatting
        let apiUrl = API_BASE_URL.endsWith('/') 
            ? `${API_BASE_URL}vehicles/search` 
            : `${API_BASE_URL}/vehicles/search`;
            
        console.log('Using Spring Boot API...');
        console.log('Fetching data from:', apiUrl);
        console.log('Query parameters:', queryParams.toString());
        
        // Add fields we specifically want to retrieve
        queryParams.append('fields', 'whatsapp,whatsappNumber,whatsappNo,alternateContact,alternateNumber,alternateContactNumber');
        
        // Log the complete URL for debugging
        const fullUrl = `${apiUrl}?${queryParams.toString()}`;
        console.log('Full API URL with all parameters:', fullUrl);
        
        // Try API call with a timeout
        fetch(fullUrl, {
            method: 'GET',
            headers: {
                'Content-Type': 'application/json',
                'Accept': 'application/json'
            },
            // Set a longer timeout
            signal: AbortSignal.timeout(20000) // 20 second timeout
        })
        .then(response => {
            console.log('Spring Boot API response status:', response.status);
            
            if (!response.ok) {
                throw new Error(`Server error: ${response.status} ${response.statusText}`);
            }
            
            return response.json().catch(error => {
                console.error('Error parsing JSON:', error);
                throw new Error('Invalid JSON response from server');
            });
        })
        .then(data => {
            console.log('Search results from Spring Boot API:', data);
            
            if (data.success && data.vehicles && data.vehicles.length > 0) {
                // Log the first vehicle to check if highlights are included
                console.log('First vehicle data sample:', data.vehicles[0]);
                
                // Check for WhatsApp and alternate contact numbers specifically
                console.log('WhatsApp fields in API response:', {
                    whatsapp: data.vehicles[0].whatsapp,
                    whatsappNumber: data.vehicles[0].whatsappNumber,
                    whatsappNo: data.vehicles[0].whatsappNo
                });
                
                console.log('Alternate contact fields in API response:', {
                    alternateContact: data.vehicles[0].alternateContact,
                    alternateNumber: data.vehicles[0].alternateNumber,
                    alternateContactNumber: data.vehicles[0].alternateContactNumber
                });
                
                // Check for highlights specifically
                if (data.vehicles[0].highlights) {
                    console.log('Highlights found in API response:', data.vehicles[0].highlights);
                } else {
                    console.warn('No highlights found in the first vehicle of API response');
                }
                
                totalPages = data.totalPages || 1;
                totalItems = data.totalItems || data.vehicles.length;
                displayResults(data.vehicles);
                updatePaginationControls();
            } else {
                console.log('No vehicles found through Spring Boot API');
                displayResults([]);
            }
        })
        .catch(error => {
            console.error('Error with Spring Boot API:', error);
            
            // Hide loading overlay
            loadingOverlay.style.display = 'none';
            
            // Show error toast with specific message based on error
            let errorMessage = 'Server error. Please try again later.';
            if (error && (error.name === 'AbortError' || /Failed to fetch|Network/i.test(error.message || ''))) {
                errorMessage = 'Server is not running or unreachable.';
            }
            showToast(errorMessage, 'error');
            
            // Show no results
            displayResults([]);
        });
    }
    
    // Client-side sorting removed; server returns correct ordering.
    
    // Function to display search results
    function displayResults(results) {
        // Hide loading overlay
        loadingOverlay.style.display = 'none';
        
        // Normalize results to handle different data formats
        let normalizedResults = [];
        
        try {
            if (!results) {
                // If results is null or undefined
                normalizedResults = [];
            } else if (typeof results === 'object' && !Array.isArray(results)) {
                // If results is an object but not an array, it might be a single vehicle or have a vehicles property
                if (results.vehicles && Array.isArray(results.vehicles)) {
                    normalizedResults = results.vehicles;
                } else if (results.id || results.vehicleId) {
                    // It's a single vehicle object
                    normalizedResults = [results];
                } else {
                    console.error('Unexpected results format:', results);
                    normalizedResults = [];
                }
            } else if (Array.isArray(results)) {
                // If results is already an array
                normalizedResults = results;
            } else {
                console.error('Unexpected results type:', typeof results);
                normalizedResults = [];
            }
            
            // Add additional validation - filter out any invalid results
            normalizedResults = normalizedResults.filter(vehicle => {
                return vehicle && typeof vehicle === 'object' && 
                    (vehicle.id || vehicle.vehicleId || vehicle.registrationNumber);
            });
            
            // Process all vehicles to normalize their data
            normalizedResults.forEach(vehicle => {
                // Debug: Log each vehicle before processing
                console.log('Processing vehicle ID: ' + (vehicle.id || 'unknown'), 
                           'has highlights object:', !!vehicle.highlights, 
                           'has serviceHighlights array:', !!vehicle.serviceHighlights);
                
                // Process service highlights
                if (!vehicle.serviceHighlights) {
                    vehicle.serviceHighlights = [];
                    
                    // Extract highlights from the database format
                    if (vehicle.highlights) {
                        const h = vehicle.highlights;
                        console.log('Vehicle highlights from API:', h);
                        if (h.highlight1) vehicle.serviceHighlights.push(h.highlight1);
                        if (h.highlight2) vehicle.serviceHighlights.push(h.highlight2);
                        if (h.highlight3) vehicle.serviceHighlights.push(h.highlight3);
                        if (h.highlight4) vehicle.serviceHighlights.push(h.highlight4);
                        if (h.highlight5) vehicle.serviceHighlights.push(h.highlight5);
                    } else {
                        // Check direct properties
                        if (vehicle.highlight1) vehicle.serviceHighlights.push(vehicle.highlight1);
                        if (vehicle.highlight2) vehicle.serviceHighlights.push(vehicle.highlight2);
                        if (vehicle.highlight3) vehicle.serviceHighlights.push(vehicle.highlight3);
                        if (vehicle.highlight4) vehicle.serviceHighlights.push(vehicle.highlight4);
                        if (vehicle.highlight5) vehicle.serviceHighlights.push(vehicle.highlight5);
                    }
                }
            });
            
            // Persist latest normalized set for client-side re-sorting
            searchResults = normalizedResults;

            // Clear previous results
            vehiclesGrid.innerHTML = '';
            
            // Update results count
            resultsCount.textContent = `Showing ${normalizedResults.length} of ${totalItems} vehicles (Page ${currentPage} / ${totalPages})`;
            
            // Show/hide no results message
            if (normalizedResults.length === 0) {
                noResults.style.display = 'block';
                return;
            } else {
                noResults.style.display = 'none';
            }
            
            // Create and append vehicle cards
            normalizedResults.forEach(vehicle => {
                try {
                    const vehicleCard = createVehicleCard(vehicle);
                    vehiclesGrid.appendChild(vehicleCard);
                } catch (err) {
                    console.error('Error creating vehicle card:', err, vehicle);
                }
            });
            // Initialize viewport-aware lazy loading for thumbnails
            requestAnimationFrame(initThumbnailObserver);
            
            // Add a debugging log
            console.log('Displayed vehicles:', normalizedResults);
        } catch (error) {
            console.error('Error displaying results:', error);
            
            // Show error toast
            showToast('Error displaying vehicles. Please try again.', 'error');
            
            // Show no results
            noResults.style.display = 'block';
            resultsCount.textContent = 'Error displaying vehicles';
        }
    }
    
    // Restore pretty URL mapping: convert Supabase public URL to /images/vehicles/:id/:file for sharing.
    function toPrettyImageUrl(url) {
        if (!url || typeof url !== 'string') return url;
        const marker = '/storage/v1/object/public/vehicle-images/';
        if (url.includes('supabase.co') && url.includes(marker)) {
            try {
                const after = url.split(marker)[1]; // e.g. "79/filename.webp"
                return `${window.location.origin}/images/vehicles/${after}`;
            } catch (_) {
                return url; // fallback
            }
        }
        return url;
    }

    // Function that attempts to handle image URLs intelligently with retry and fallback mechanisms
    function getImageUrlsForVehicle(vehicle, callback) {
        // Start with existing methods if available
        if (vehicle.images && vehicle.images.length > 0) {
            console.log('Using existing images array:', vehicle.images);
            const mapped = vehicle.images.map(toPrettyImageUrl);
            callback(mapped);
            return;
        }
        
        if (vehicle.vehicleImageUrls && vehicle.vehicleImageUrls.length > 0) {
            console.log('Using existing vehicleImageUrls array:', vehicle.vehicleImageUrls);
            const validImages = vehicle.vehicleImageUrls.filter(url => 
                url && typeof url === 'string' && 
                !url.endsWith('.hidden_folder') && 
                !url.endsWith('.folder') && 
                url.trim() !== '');
                
            if (validImages.length > 0) {
                const mapped = validImages.map(toPrettyImageUrl);
                vehicle.images = mapped;
                callback(mapped);
                return;
            }
        }
        
        if (vehicle.vehicle_image_urls_json) {
            try {
                let imageUrls = JSON.parse(vehicle.vehicle_image_urls_json);
                console.log('Parsed image URLs from JSON:', imageUrls);
                
                const validImages = imageUrls.filter(url => 
                    url && typeof url === 'string' && 
                    !url.endsWith('.hidden_folder') && 
                    !url.endsWith('.folder') && 
                    url.trim() !== '');
                    
                if (validImages.length > 0) {
                    const mapped = validImages.map(toPrettyImageUrl);
                    vehicle.images = mapped;
                    callback(mapped);
                    return;
                }
            } catch (err) {
                console.error('Error parsing vehicle_image_urls_json:', err);
            }
        }
        
        // If we have a vehicle ID but no images yet, try to fetch from API
        if (vehicle.id) {
            console.log(`Fetching images for vehicle ID: ${vehicle.id}`);
            const apiUrl = `${API_BASE_URL}registration-images/${vehicle.id}`;
            
            // Attempt a direct fetch with timeout
            const fetchWithTimeout = (url, options, timeout = 5000) => {
                return Promise.race([
                    fetch(url, options),
                    new Promise((_, reject) => 
                        setTimeout(() => reject(new Error('Request timed out')), timeout)
                    )
                ]);
            };
            
            fetchWithTimeout(apiUrl, {
                method: 'GET',
                headers: {
                    'Accept': 'application/json',
                    'Origin': window.location.origin
                }
            }, 5000)
            .then(response => {
                if (!response.ok) {
                    throw new Error(`HTTP error! Status: ${response.status}`);
                }
                return response.json();
            })
            .then(data => {
                console.log('Received image data from API:', data);
                const imageUrls = data.imageUrls || [];
                
                if (imageUrls && imageUrls.length > 0) {
                    const validImages = imageUrls.filter(url => 
                        url && typeof url === 'string' && 
                        !url.endsWith('.hidden_folder') && 
                        !url.endsWith('.folder') && 
                        url.trim() !== '');
                        
                    if (validImages.length > 0) {
                        const mapped = validImages.map(toPrettyImageUrl);
                        vehicle.images = mapped;
                        callback(mapped);
                        return;
                    }
                }
                // If we get here, no valid images were found in the API response
                fallbackToDirectUrl();
            })
            .catch(error => {
                console.error('Error fetching images from API:', error);
                fallbackToDirectUrl();
            });
        } else {
            // No ID or other image sources, use default
            callback(['attached_assets/images/default-vehicle.png']);
        }
        
        // Try direct URL construction as fallback if everything else fails
        function fallbackToDirectUrl() {
            console.log('Falling back to direct URL construction');
            
            // Try to construct direct URLs based on registration ID patterns
            if (vehicle.id) {
                const registrationId = vehicle.id;
                
                // Construct URLs for possible image locations
                const base = window.API_BASE_URL || 'http://localhost:8080';
                const possibleUrls = [
                    `${base}/api/file/registration/${registrationId}/front.jpg`,
                    `${base}/api/file/registrations/${registrationId}/front.jpg`,
                    `${base}/api/file/vehicles/${registrationId}/front.jpg`,
                    `${base}/supabase-images/registration/${registrationId}/front.jpg`
                ];
                
                console.log('Trying fallback URLs:', possibleUrls);
                vehicle.images = possibleUrls;
                callback(possibleUrls);
            } else {
                // No ID available, use default image
                callback(['attached_assets/images/default-vehicle.png']);
            }
        }
    }
    
    // Function to create a vehicle card
    function createVehicleCard(vehicle) {
        const card = document.createElement('div');
        card.className = 'vehicle-card';
        card.dataset.id = vehicle.id || vehicle.vehicleId || '';
        
        // Check if this is a premium user's vehicle
        // First check if membership is directly on the vehicle object
        const isPremium = vehicle.membership === 'Premium';
        
        // Log membership status for debugging
        console.log(`Vehicle ${vehicle.id} membership status: ${vehicle.membership || 'Not set'}`);
        
        if (isPremium) {
            card.classList.add('premium-vehicle');
            console.log(`Vehicle ${vehicle.id} marked as premium`);
        }
        
        // Handle image paths - check for images from different sources and folder structure
        let mainImage = 'attached_assets/images/default-vehicle.png'; // Default image
        
        console.log('Creating vehicle card for vehicle:', vehicle);
        
        // Lazy thumbnail loading with skeleton
        const imgContainer = document.createElement('div');
        imgContainer.className = 'vehicle-image loading';
        const skeleton = document.createElement('div');
        skeleton.className = 'image-skeleton';
        skeleton.style.width = '100%';
        skeleton.style.height = '100%';
        imgContainer.appendChild(skeleton);
        const img = document.createElement('img');
        img.alt = vehicle.fullName ? `${vehicle.fullName}'s ${vehicle.vehicleType}` : 'Vehicle Image';
        img.setAttribute('data-thumb','true');
        let candidates = Array.isArray(vehicle.images) ? vehicle.images.slice() : [];
        if (!candidates.length && Array.isArray(vehicle.vehicleImageUrls)) candidates = vehicle.vehicleImageUrls.slice();
        if (!candidates.length && vehicle.vehicle_image_urls_json) {
            try { const arr = JSON.parse(vehicle.vehicle_image_urls_json); if (Array.isArray(arr)) candidates = arr; } catch(_){}
        }
        candidates = candidates.filter(u => typeof u === 'string' && u.trim() && !u.endsWith('.hidden_folder') && !u.endsWith('.folder'));
        const primary = candidates[0] ? toPrettyImageUrl(candidates[0]) : 'attached_assets/images/default-vehicle.png';
        img.dataset.src = primary;
        img.loading = 'lazy';
        img.style.opacity = '0';
        imgContainer.appendChild(img);
        const retryOverlay = document.createElement('div');
        retryOverlay.className = 'thumb-retry hidden';
        retryOverlay.innerHTML = '<span>Image failed</span><button type="button">Retry</button>';
        imgContainer.appendChild(retryOverlay);
        retryOverlay.querySelector('button').addEventListener('click', () => {
            retryOverlay.classList.add('hidden');
            loadThumbnailImage(img, img.dataset.src, imgContainer, retryOverlay, 1);
        });
        
        // Process WhatsApp number - check all possible field names
        // This ensures we don't use contact number as fallback
        if (vehicle.whatsappNumber && vehicle.whatsappNumber !== '-') {
            // If whatsappNumber exists, use it and make sure whatsapp is set too
            vehicle.whatsapp = vehicle.whatsappNumber;
        } else if (vehicle.whatsapp && vehicle.whatsapp !== '-') {
            // If whatsapp exists, use it and make sure whatsappNumber is set too
            vehicle.whatsappNumber = vehicle.whatsapp;
        }
        
        // Log WhatsApp number for debugging
        console.log(`Vehicle ${vehicle.id || 'unknown'} WhatsApp data:`, {
            whatsappNumber: vehicle.whatsappNumber,
            whatsapp: vehicle.whatsapp
        });
        
        // Make a direct API call to get the complete vehicle data if we have an ID
        if (vehicle.id) {
            // Use a more specific endpoint that's likely to return complete vehicle data
            const apiUrl = `${API_BASE_URL}registration/${vehicle.id}?_t=${Date.now()}`;
            console.log(`Fetching complete data for vehicle card from: ${apiUrl}`);
            
            fetch(apiUrl)
                .then(response => response.json())
                .then(data => {
                    console.log(`Direct API response for vehicle card ${vehicle.id}:`, data);
                    
                    // Update WhatsApp number if found in API response
                    if (data.whatsapp || data.whatsappNumber || data.whatsappNo) {
                        const newWhatsapp = data.whatsapp || data.whatsappNumber || data.whatsappNo;
                        console.log(`Updated WhatsApp for vehicle ${vehicle.id} from direct API:`, newWhatsapp);
                        
                        // Update the vehicle object
                        vehicle.whatsapp = newWhatsapp;
                        vehicle.whatsappNumber = newWhatsapp;
                    }
                    
                    // Update alternate contact if found in API response
                    if (data.alternateContact || data.alternateNumber || data.alternateContactNumber) {
                        const newAlternate = data.alternateContact || data.alternateNumber || data.alternateContactNumber;
                        console.log(`Updated alternate contact for vehicle ${vehicle.id} from direct API:`, newAlternate);
                        
                        // Update the vehicle object
                        vehicle.alternateContact = newAlternate;
                        vehicle.alternateNumber = newAlternate;
                        vehicle.alternateContactNumber = newAlternate;
                    }
                    
                    // Update membership if found in API response
                    if (data.membership) {
                        console.log(`Updated membership for vehicle ${vehicle.id} from direct API: ${data.membership}`);
                        vehicle.membership = data.membership;
                        
                        // If this vehicle is premium, update the card styling
                        if (data.membership === 'Premium' && !card.classList.contains('premium-vehicle')) {
                            card.classList.add('premium-vehicle');
                            
                            // Add premium badge and ribbon
                            if (!imgContainer.querySelector('.premium-badge')) {
                                const premiumBadge = document.createElement('div');
                                premiumBadge.className = 'premium-badge';
                                premiumBadge.innerHTML = '<video autoplay loop muted playsinline preload="auto" aria-label="Premium Vehicle"><source src="attached_assets/animation/premium-vehicle.webm" type="video/webm"></video>';
                                imgContainer.appendChild(premiumBadge);
                                
                                const premiumRibbon = document.createElement('div');
                                premiumRibbon.className = 'premium-ribbon';
                                imgContainer.appendChild(premiumRibbon);
                            }
                            
                            // Update title and button styling
                            const title = detailsContainer.querySelector('.vehicle-title');
                            if (title) title.classList.add('premium-title');
                            
                            const button = detailsContainer.querySelector('.view-details-btn');
                            if (button) button.classList.add('premium-btn');
                        }
                    }
                })
                .catch(error => {
                    console.error(`Error fetching complete data for vehicle ${vehicle.id}:`, error);
                });
        }
        
        vehicle.contactNumber = vehicle.contactNumber || vehicle.contact || vehicle.phone || '';
        
        // Check if the vehicle is newly registered (less than one month old)
        const registrationDate = vehicle.registrationDate || vehicle.regDate || vehicle.date || '';
        let isNewVehicle = false;
        let isVerified = false;
        
        if (registrationDate) {
            try {
                const regDate = new Date(registrationDate);
                const currentDate = new Date();
                
                // Calculate the difference in milliseconds
                const diffTime = currentDate - regDate;
                // Convert to days
                const diffDays = Math.floor(diffTime / (1000 * 60 * 60 * 24));
                
                // If less than 30 days, it's a new vehicle
                if (diffDays < 30) {
                    isNewVehicle = true;
                } else {
                    // If more than 30 days, it's verified
                    isVerified = true;
                }
                
                console.log(`Vehicle ID: ${vehicle.id}, Registration Date: ${registrationDate}, Age: ${diffDays} days, New: ${isNewVehicle}, Verified: ${isVerified}`);
            } catch (error) {
                console.error('Error calculating vehicle age:', error);
            }
        }
        
        // Add new vehicle badge if applicable
        if (isNewVehicle) {
            const newBadge = document.createElement('div');
            newBadge.className = 'new-vehicle-badge';
            newBadge.innerHTML = '<img src="attached_assets/images/new (1).webp" alt="New Vehicle">';
            imgContainer.appendChild(newBadge);
        }
        // Add verified badge if applicable (and not premium)
        else if (isVerified && !isPremium) {
            const verifiedBadge = document.createElement('div');
            verifiedBadge.className = 'verified-badge';
            verifiedBadge.innerHTML = '<img src="attached_assets/images/verify.webp" alt="Verified Vehicle">';
            imgContainer.appendChild(verifiedBadge);
        }
        
        // Add premium badge and ribbon if applicable
        if (isPremium) {
            // Add premium badge with GIF
            const premiumBadge = document.createElement('div');
            premiumBadge.className = 'premium-badge';
            premiumBadge.innerHTML = '<video autoplay loop muted playsinline preload="auto" aria-label="Premium Vehicle"><source src="attached_assets/animation/premium-vehicle.webm" type="video/webm"></video>';
            imgContainer.appendChild(premiumBadge);
            
            // Add premium ribbon
            const premiumRibbon = document.createElement('div');
            premiumRibbon.className = 'premium-ribbon';
            imgContainer.appendChild(premiumRibbon);
        }
        
        // Create details container
        const detailsContainer = document.createElement('div');
        detailsContainer.className = 'vehicle-details';

        // Create vehicle details variables
        const vehicleName = vehicle.name || vehicle.vehicleName || vehicle.fullName || `${vehicle.type || vehicle.vehicleType || 'Vehicle'} #${vehicle.id || ''}`;
        const vehicleCity = vehicle.locationCity || vehicle.city || 'Unknown City';
        const vehicleState = vehicle.locationState || vehicle.state || 'Unknown State';
        const vehiclePincodeCard = vehicle.locationPincode || vehicle.pincode || '-';
        const ownerName = vehicle.ownerName || vehicle.driverName || vehicle.owner || vehicle.fullName || 'Owner';

        const vehicleNameEsc = escapeHtml(vehicleName);
        const vehicleCityEsc = escapeHtml(vehicleCity);
        const vehicleStateEsc = escapeHtml(vehicleState);
        const vehiclePincodeCardEsc = escapeHtml(vehiclePincodeCard);
        const ownerNameEsc = escapeHtml(ownerName);
        // Premium flag used later inside modal content rendering (scoped to modal only)
        let isPremiumForModal = false;
        try {
            const membershipVal = vehicle && vehicle.membership ? String(vehicle.membership).toLowerCase() : '';
            isPremiumForModal = membershipVal === 'premium';
        } catch (e) {
            isPremiumForModal = false;
        }
        
        // Get trust counter value (default to 0 if not available)
        const trustCount = vehicle.call_tracking || vehicle.callTracking || 0;
        
        // Get trust indicator text based on trust count
        function getTrustIndicatorText() {
            if (isPremium) {
                return `<div class="info-item premium-info-item">
                    <i class="fas fa-shield-alt" style="color: #FFD700;"></i>
                    <span>Premium Verified Provider</span>
                </div>`;
            } else if (trustCount === 0) {
                return '';
            } else if (trustCount >= 1 && trustCount <= 4) {
                return `<div class="info-item">
                    <i class="fas fa-star-half-alt" style="color: #ffc107;"></i>
                    <span>${trustCount} user${trustCount !== 1 ? 's' : ''} showed interest</span>
                </div>`;
            } else if (trustCount >= 5 && trustCount <= 10) {
                return `<div class="info-item">
                    <i class="fas fa-star" style="color: #4caf50;"></i>
                    <span>Getting noticed in your area</span>
                </div>`;
            } else {
                return `<div class="info-item">
                    <i class="fas fa-crown" style="color: #673ab7;"></i>
                    <span>Most trusted in your area</span>
                </div>`;
            }
        }
        
        // Ensure service highlights are processed
        if (!vehicle.serviceHighlights) {
            vehicle.serviceHighlights = [];
            
            // Extract from highlights object if it exists
            if (vehicle.highlights) {
                const h = vehicle.highlights;
                if (h.highlight1) vehicle.serviceHighlights.push(h.highlight1);
                if (h.highlight2) vehicle.serviceHighlights.push(h.highlight2);
                if (h.highlight3) vehicle.serviceHighlights.push(h.highlight3);
                if (h.highlight4) vehicle.serviceHighlights.push(h.highlight4);
                if (h.highlight5) vehicle.serviceHighlights.push(h.highlight5);
            } else {
                // Check for direct properties
                if (vehicle.highlight1) vehicle.serviceHighlights.push(vehicle.highlight1);
                if (vehicle.highlight2) vehicle.serviceHighlights.push(vehicle.highlight2);
                if (vehicle.highlight3) vehicle.serviceHighlights.push(vehicle.highlight3);
                if (vehicle.highlight4) vehicle.serviceHighlights.push(vehicle.highlight4);
                if (vehicle.highlight5) vehicle.serviceHighlights.push(vehicle.highlight5);
            }
        }
        
        // Create highlights HTML if any highlights exist
        let highlightsHTML = '';
        if (vehicle.serviceHighlights && vehicle.serviceHighlights.length > 0) {
            highlightsHTML = `
                <div class="service-highlights ${isPremium ? 'premium-highlights' : ''}">
                    ${vehicle.serviceHighlights.slice(0, 2).map(highlight => 
                        `<span class="highlight-tag ${isPremium ? 'premium-tag' : ''}">${escapeHtml(highlight)}</span>`
                    ).join('')}
                    ${vehicle.serviceHighlights.length > 2 ? `<span class="highlight-more ${isPremium ? 'premium-more' : ''}"">+${vehicle.serviceHighlights.length - 2}</span>` : ''}
                </div>
            `;
        }
        
        // Check for description - if it's a number or empty, use default text
        let description = vehicle.description || 'No description provided';
        
        // Handle special cases in the description
        if (description && typeof description === 'string') {
            // If the description starts with our 'text:' prefix, remove it
            if (description.startsWith('text:')) {
                description = description.substring(5);
            }
            
            // If description is empty after processing
            if (!description.trim()) {
                description = 'No description provided';
            }
            // If description contains only numbers, use a default description
            else if (/^\d+$/.test(description.trim())) {
            description = "This vehicle is available for transport services. Contact the owner for more details.";
            }
        }
        
        // Text skeleton + real content (real hidden until image load completes)
        const skeletonBlock = document.createElement('div');
        skeletonBlock.className = 'details-skeleton';
        skeletonBlock.innerHTML = '<div class="text-skel line1"></div><div class="text-skel line2"></div><div class="text-skel line3"></div>';
        const realDetails = document.createElement('div');
        realDetails.className = 'details-real pending';
        realDetails.innerHTML = `
            <h3 class="vehicle-title ${isPremium ? 'premium-title' : ''}">${vehicleNameEsc}</h3>
            <div class="vehicle-info">
                <div class="info-item">
                    <i class="fas fa-map-marker-alt"></i>
                    <span>${vehicleCityEsc}, ${vehicleStateEsc}</span>
                </div>
                <div class="info-item">
                    <i class="fas fa-thumbtack"></i>
                    <span>${vehiclePincodeCardEsc}</span>
                </div>
                <div class="info-item">
                    <i class="fas fa-user"></i>
                    <span>${ownerNameEsc}</span>
                </div>
                ${getTrustIndicatorText()}
                ${highlightsHTML}
            </div>
            <div class="vehicle-bottom">
                <button class="view-details-btn ${isPremium ? 'premium-btn' : ''}">View Details</button>
            </div>`;
        detailsContainer.appendChild(skeletonBlock);
        detailsContainer.appendChild(realDetails);
        
        // Assemble the card
        card.appendChild(imgContainer);
        card.appendChild(detailsContainer);
        
        // Add click event to view details button
        card.querySelector('.view-details-btn').addEventListener('click', function() {
            openVehicleModal(vehicle);
        });
        
        return card;
    }

    // Thumbnail loader with retry attempts (2) then show retry overlay
    function loadThumbnailImage(imgEl, url, container, retryOverlay, attempt=0) {
        if (!url) return;
        const MAX_ATTEMPTS = 2;
        const probe = new Image();
        probe.onload = () => {
            imgEl.src = url;
            container.classList.remove('loading');
            container.classList.add('loaded');
            const sk = container.querySelector('.image-skeleton');
            if (sk) sk.remove();
            requestAnimationFrame(() => { imgEl.style.transition='opacity .35s'; imgEl.style.opacity='1'; });
            const card = container.closest('.vehicle-card');
            if (card) {
                const ds = card.querySelector('.details-skeleton');
                const real = card.querySelector('.details-real');
                if (ds) ds.remove();
                if (real) real.classList.remove('pending');
            }
        };
        probe.onerror = () => {
            console.warn('Thumbnail load error', url, 'attempt', attempt);
            if (attempt < MAX_ATTEMPTS) {
                setTimeout(() => loadThumbnailImage(imgEl, url, container, retryOverlay, attempt+1), 700);
            } else {
                const sk = container.querySelector('.image-skeleton');
                if (sk) sk.remove();
                // Show retry overlay but still reveal real details and set fallback image
                retryOverlay.classList.remove('hidden');
                if (!imgEl.src) {
                    imgEl.src = 'attached_assets/images/default-vehicle.png';
                    requestAnimationFrame(()=>{ imgEl.style.transition='opacity .35s'; imgEl.style.opacity='1'; });
                }
                const card = container.closest('.vehicle-card');
                if (card) {
                    const ds = card.querySelector('.details-skeleton');
                    const real = card.querySelector('.details-real');
                    if (ds) ds.remove();
                    if (real) real.classList.remove('pending');
                }
            }
        };
        probe.src = url;
    }

    function loadThumbnailsForCurrentPage() {
        document.querySelectorAll('.vehicle-card .vehicle-image img[data-thumb]').forEach(img => {
            if (!img.src && img.dataset.src) {
                const container = img.closest('.vehicle-image');
                const retry = container.querySelector('.thumb-retry');
                loadThumbnailImage(img, img.dataset.src, container, retry);
            }
        });
    }
    // IntersectionObserver based lazy load
    function initThumbnailObserver() {
        if (!('IntersectionObserver' in window)) { loadThumbnailsForCurrentPage(); return; }
        const observer = new IntersectionObserver(entries => {
            entries.forEach(entry => {
                if (entry.isIntersecting) {
                    const img = entry.target.querySelector('img[data-thumb]');
                    if (img && !img.src && img.dataset.src) {
                        const container = img.closest('.vehicle-image');
                        const retry = container.querySelector('.thumb-retry');
                        loadThumbnailImage(img, img.dataset.src, container, retry);
                    }
                    observer.unobserve(entry.target);
                }
            });
        }, { root:null, rootMargin:'120px', threshold:0.05 });
        document.querySelectorAll('.vehicle-card .vehicle-image').forEach(el => observer.observe(el));
    }
    
    // Function to open vehicle details modal
    function openVehicleModal(vehicle) {
        // Always open the modal immediately to avoid any blocking due to downstream errors
        try {
            if (vehicleModal && vehicleModal.style.display !== 'block') {
                vehicleModal.style.display = 'block';
                // Set temporary skeleton/loading state up-front
                if (modalHeader) {
                    modalHeader.innerHTML = `
                        <div class="loading-indicator">
                            <i class="fas fa-spinner fa-spin"></i>
                            <span>Loading vehicle details...</span>
                        </div>
                    `;
                }
                if (modalGallery) {
                    modalGallery.innerHTML = '<div class="loading-indicator"><i class="fas fa-spinner fa-spin"></i></div>';
                }
                if (modalInfo) {
                    modalInfo.innerHTML = '';
                }
            }
        } catch (e) {
            console.warn('Failed to show modal immediately:', e);
        }
        // Handle all possible field names and provide defaults
        const vehicleName = vehicle.name || vehicle.vehicleName || vehicle.fullName || `${vehicle.type || vehicle.vehicleType || 'Vehicle'} #${vehicle.id || ''}`;
        const vehicleType = vehicle.type || vehicle.vehicleType || 'Vehicle';
        const vehicleCity = vehicle.locationCity || vehicle.city || 'Unknown';
        const vehicleState = vehicle.locationState || vehicle.state || 'Unknown';
        const vehiclePincode = vehicle.locationPincode || vehicle.pincode || '-';
    const ownerName = vehicle.ownerName || vehicle.driverName || vehicle.owner || vehicle.fullName || 'Owner';
        const ownerPhone = vehicle.ownerPhone || vehicle.driverPhone || vehicle.contactNumber || vehicle.contact || vehicle.phone || '-';

        const vehicleNameEsc = escapeHtml(vehicleName);
        const vehicleTypeEsc = escapeHtml(vehicleType);
        const vehicleCityEsc = escapeHtml(vehicleCity);
        const vehicleStateEsc = escapeHtml(vehicleState);
        const vehiclePincodeEsc = escapeHtml(vehiclePincode);
        const ownerNameEsc = escapeHtml(ownerName);
        const ownerPhoneEsc = escapeHtml(ownerPhone);
    // Modal-scoped premium flag used in rendering below
    const isPremiumForModal = (vehicle && vehicle.membership ? String(vehicle.membership).toLowerCase() : '') === 'premium';
        // Check all possible field names for WhatsApp number
        let whatsappNumber = '';
        
        if (vehicle.whatsappNumber && vehicle.whatsappNumber !== '-') {
            whatsappNumber = vehicle.whatsappNumber;
        } else if (vehicle.whatsapp && vehicle.whatsapp !== '-') {
            whatsappNumber = vehicle.whatsapp;
        } else if (vehicle.whatsappNo && vehicle.whatsappNo !== '-') {
            whatsappNumber = vehicle.whatsappNo;
        }
        
        // For debugging
        console.log('WhatsApp number from vehicle data:', { 
            finalWhatsappNumber: whatsappNumber,
            fromWhatsappNumber: vehicle.whatsappNumber,
            fromWhatsapp: vehicle.whatsapp,
            fromWhatsappNo: vehicle.whatsappNo,
            vehicleId: vehicle.id
        });
        
        // Helper to generate contact info markup consistently (copy button support)
        function createContactInfoWithCopy(value) {
            if (value === '-' || value === '' || value == null) {
                return `<div class="info-value">-</div>`;
            }
            const raw = String(value);
            const safeText = escapeHtml(raw);
            const safeAttr = escapeHtml(raw);
            return `
                <div class="info-value-container">
                    <div class="info-value">${safeText}</div>
                    <i class="fas fa-copy copy-btn" title="Copy to clipboard" data-value="${safeAttr}"></i>
                </div>
            `;
        }

        // Make a direct API call to get the complete vehicle data
        if (vehicle.id) {
            // Use a more specific endpoint that's likely to return complete vehicle data
            const apiUrl = `${API_BASE_URL}registration/${vehicle.id}`;
            console.log(`Fetching complete vehicle data from: ${apiUrl}`);
            
            fetch(apiUrl)
                .then(response => response.json())
                .then(data => {
                    console.log('Direct API response for this vehicle:', data);
                    
                    // Check if RC and DL documents exist in the API response
                    if (data.rc || data.d_l) {
                        console.log("API returned document data:", { rc: data.rc, dl: data.d_l });
                        
                        // Update vehicle object with document data
                        if (data.rc) vehicle.rc = data.rc;
                        if (data.d_l) vehicle.d_l = data.d_l;
                        
                        // Update document badges
                        const documentBadgesContainer = document.querySelector('.document-badges-container');
                        if (documentBadgesContainer) {
                            // Clear and recreate the badges with the updated data
                            documentBadgesContainer.innerHTML = '';
                            
                            // Create RC badge
                            const rcBadge = document.createElement('div');
                            rcBadge.className = 'document-badge-item';
                            rcBadge.innerHTML = `
                                <div class="document-badge-icon">
                                    <i class="fas fa-id-card"></i>
                                </div>
                                <div class="document-badge-info">
                                    <div class="document-badge-title">Vehicle RC</div>
                                    <div class="document-badge-status ${data.rc ? 'verified' : 'not-verified'}">
                                        <i class="fas ${data.rc ? 'fa-check-circle' : 'fa-times-circle'}"></i> 
                                        ${data.rc ? 'Verified' : 'Not Verified'}
                                    </div>
                                    <div class="document-badge-meaning">
                                        ${data.rc ? 'Trusted Owner Badge' : 'Registration Certificate not uploaded'}
                                    </div>
                                </div>
                            `;
                            
                            // Create DL badge
                            const dlBadge = document.createElement('div');
                            dlBadge.className = 'document-badge-item';
                            dlBadge.innerHTML = `
                                <div class="document-badge-icon">
                                    <i class="fas fa-id-badge"></i>
                                </div>
                                <div class="document-badge-info">
                                    <div class="document-badge-title">Driver License</div>
                                    <div class="document-badge-status ${data.d_l ? 'verified' : 'not-verified'}">
                                        <i class="fas ${data.d_l ? 'fa-check-circle' : 'fa-times-circle'}"></i>
                                        ${data.d_l ? 'Verified' : 'Not Verified'}
                                    </div>
                                    <div class="document-badge-meaning">
                                        ${data.d_l ? 'Licensed Driver Badge' : 'Driver\'s License not uploaded'}
                                    </div>
                                </div>
                            `;
                            
                            // Add badges to container
                            documentBadgesContainer.appendChild(rcBadge);
                            documentBadgesContainer.appendChild(dlBadge);
                        }
                    }
                    
                    // Update WhatsApp number if found in API response
                    if (data.whatsapp || data.whatsappNumber || data.whatsappNo) {
                        whatsappNumber = data.whatsapp || data.whatsappNumber || data.whatsappNo;
                        console.log('Updated WhatsApp number from direct API call:', whatsappNumber);
                        
                        // Update the WhatsApp section in the modal (if already rendered), otherwise retry shortly
                        const updateWhatsAppSection = () => {
                            const whatsappSection = (typeof modalInfo !== 'undefined' && modalInfo && modalInfo.querySelector)
                                ? modalInfo.querySelector('[data-field="owner-whatsapp"]')
                                : document.querySelector('[data-field="owner-whatsapp"]');
                            if (whatsappSection) {
                                whatsappSection.innerHTML = `
                                    <div class="info-label">WhatsApp</div>
                                    ${whatsappNumber ? createContactInfoWithCopy(whatsappNumber) : '<div class="info-value">WhatsApp number not provided by vehicle owner</div>'}
                                `;
                            }
                        };
                        updateWhatsAppSection();
                        setTimeout(updateWhatsAppSection, 500);
                        
                        // Update the contact button
                        if (whatsappNumber && whatsappNumber !== '-' && whatsappNumber !== '') {
                            contactDriverBtn.innerHTML = '<i class="fab fa-whatsapp"></i> Contact via WhatsApp';
                            contactDriverBtn.onclick = function() {
                                const cleanNumber = whatsappNumber.replace(/\D/g, '');
                                const message = encodeURIComponent(
                                    "Hello, I got your contact from HerapheriGoods. I want to know more about your vehicle service.\nनमस्ते, मुझे आपका संपर्क HerapheriGoods से मिला है। मैं आपकी वाहन सेवा के बारे में और जानना चाहता हूँ।"
                                );
                                window.open(`https://wa.me/${cleanNumber}?text=${message}`, '_blank');
                            };
                            contactDriverBtn.style.display = 'inline-block';
                        } else {
                            contactDriverBtn.style.display = 'none';
                        }
                    }
                    
                    // Update alternate contact if found in API response
                    if (data.alternateContact || data.alternateNumber || data.alternateContactNumber) {
                        const newAlternateNumber = data.alternateContact || data.alternateNumber || data.alternateContactNumber;
                        console.log('Updated alternate number from direct API call:', newAlternateNumber);
                        
                        // Update the alternate contact section in the modal (if already rendered), otherwise retry shortly
                        const updateAlternateSection = () => {
                            const alternateSection = (typeof modalInfo !== 'undefined' && modalInfo && modalInfo.querySelector)
                                ? modalInfo.querySelector('[data-field="owner-alternate"]')
                                : document.querySelector('[data-field="owner-alternate"]');
                            if (alternateSection) {
                                alternateSection.innerHTML = `
                                    <div class="info-label">Alternate Number</div>
                                    ${createContactInfoWithCopy(newAlternateNumber)}
                                `;
                            }
                        };
                        updateAlternateSection();
                        setTimeout(updateAlternateSection, 500);
                    }
                })
                .catch(error => {
                    console.error('Error fetching complete vehicle data:', error);
                });
        }
        const alternateNumber = vehicle.alternateNumber || vehicle.alternateContactNumber || vehicle.alternateContact || '-';
        const registrationDate = vehicle.registrationDate || vehicle.regDate || vehicle.date || '';
        const registrationNumber = vehicle.registrationNumber || vehicle.regNumber || vehicle.vehicleNumber || vehicle.vehiclePlateNumber || '-';
        const vehicleId = vehicle.id || vehicle.vehicleId || '';
        const userId = vehicle.userId || vehicle.user_id || vehicle.ownerId || vehicle.owner_id || '';
        
        // Get trust counter value (default to 0 if not available)
        let trustCount = vehicle.call_tracking || vehicle.callTracking || 0;
        
        // Extract service highlights from database format
        let serviceHighlights = [];
        if (vehicle.serviceHighlights && Array.isArray(vehicle.serviceHighlights)) {
            // If serviceHighlights is already an array, use it directly
            serviceHighlights = vehicle.serviceHighlights;
        } else if (vehicle.highlights) {
            // If highlights object exists (from database format), extract all non-null values
            const highlightsObj = vehicle.highlights;
            if (highlightsObj.highlight1) serviceHighlights.push(highlightsObj.highlight1);
            if (highlightsObj.highlight2) serviceHighlights.push(highlightsObj.highlight2);
            if (highlightsObj.highlight3) serviceHighlights.push(highlightsObj.highlight3);
            if (highlightsObj.highlight4) serviceHighlights.push(highlightsObj.highlight4);
            if (highlightsObj.highlight5) serviceHighlights.push(highlightsObj.highlight5);
        } else {
            // Try to extract highlights directly if they exist on the vehicle object
            if (vehicle.highlight1) serviceHighlights.push(vehicle.highlight1);
            if (vehicle.highlight2) serviceHighlights.push(vehicle.highlight2);
            if (vehicle.highlight3) serviceHighlights.push(vehicle.highlight3);
            if (vehicle.highlight4) serviceHighlights.push(vehicle.highlight4);
            if (vehicle.highlight5) serviceHighlights.push(vehicle.highlight5);
        }
        
        // Check for description - if it's a number or empty, use default text
        let description = vehicle.description || 'No description provided';
        if (!isNaN(description) || description.trim() === '') {
            description = 'No description provided';
        }
        
        // Get modal elements
        const modalHeader = document.getElementById('modalHeader');
        const modalGallery = document.getElementById('modalGallery');
        const modalInfo = document.getElementById('modalInfo');
        const documentBadgesContainer = document.querySelector('.document-badges-container');
        const contactDriverBtn = document.getElementById('contactDriverBtn');
        const callDriverBtn = document.getElementById('callDriverBtn');
        
        // Update document badges section
        if (documentBadgesContainer) {
            documentBadgesContainer.innerHTML = ''; // Clear previous badges
            
            // Check if RC document exists
            const rcBadge = document.createElement('div');
            rcBadge.className = 'document-badge-item';
            
            if (vehicle.rc) {
                rcBadge.innerHTML = `
                    <div class="document-badge-icon">
                        <i class="fas fa-id-card"></i>
                    </div>
                    <div class="document-badge-info">
                        <div class="document-badge-title">Vehicle RC</div>
                        <div class="document-badge-status verified">
                            <i class="fas fa-check-circle"></i> Verified
                        </div>
                    </div>
                `;
            } else {
                rcBadge.innerHTML = `
                    <div class="document-badge-icon">
                        <i class="fas fa-id-card"></i>
                    </div>
                    <div class="document-badge-info">
                        <div class="document-badge-title">Vehicle RC</div>
                        <div class="document-badge-status not-verified">
                            <i class="fas fa-times-circle"></i> Not Verified
                        </div>
                    </div>
                `;
            }
            
            // Check if Driver License document exists
            const dlBadge = document.createElement('div');
            dlBadge.className = 'document-badge-item';
            
            if (vehicle.d_l) {
                dlBadge.innerHTML = `
                    <div class="document-badge-icon">
                        <i class="fas fa-id-badge"></i>
                    </div>
                    <div class="document-badge-info">
                        <div class="document-badge-title">Driver License</div>
                        <div class="document-badge-status verified">
                            <i class="fas fa-check-circle"></i> Verified
                        </div>
                    </div>
                `;
            } else {
                dlBadge.innerHTML = `
                    <div class="document-badge-icon">
                        <i class="fas fa-id-badge"></i>
                    </div>
                    <div class="document-badge-info">
                        <div class="document-badge-title">Driver License</div>
                        <div class="document-badge-status not-verified">
                            <i class="fas fa-times-circle"></i> Not Verified
                        </div>
                    </div>
                `;
            }
            
            // Add badges to container
            documentBadgesContainer.appendChild(rcBadge);
            documentBadgesContainer.appendChild(dlBadge);
        }
        
        // Show loading state (idempotent, in case we already set it above)
        if (modalHeader) {
            modalHeader.innerHTML = `
                <div class="loading-indicator">
                    <i class="fas fa-spinner fa-spin"></i>
                    <span>Loading vehicle details...</span>
                </div>
            `;
        }
        if (modalGallery) {
            modalGallery.innerHTML = '<div class="loading-indicator"><i class="fas fa-spinner fa-spin"></i></div>';
        }
        
        // Function to update the trust counter in the database
        function updateTrustCounter() {
            // Only update if we have a valid vehicle ID
            if (!vehicleId) {
                console.warn('Cannot update trust counter: No vehicle ID available');
                return;
            }
            
            // Increment local counter for immediate UI feedback
            trustCount++;
            
            // Update the trust counter display in the modal
            const trustCounterElement = document.getElementById('trustCounter');
            if (trustCounterElement) {
                trustCounterElement.textContent = trustCount;
            }
            
            // Update the trust indicator message based on new count
            updateTrustIndicatorMessage();
            
            // Prepare the API endpoint (backend only)
            const apiUrl = `${API_BASE_URL}vehicles/update-trust-counter/${vehicleId}`;
            
            console.log('Updating trust counter for vehicle ID:', vehicleId);
            
            // Backend API update only
            fetch(apiUrl, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' }
            })
            .then(response => {
                if (!response.ok) {
                    throw new Error('Failed to update trust counter');
                }
                return response.json();
            })
            .then(data => {
                console.log('Trust counter updated successfully:', data);
            })
            .catch(error => {
                console.error('Error updating trust counter:', error);
                // Revert the local counter on error
                trustCount--;
                if (trustCounterElement) {
                    trustCounterElement.textContent = trustCount;
                }
                // Also revert the trust indicator message
                updateTrustIndicatorMessage();
            });
        }
        
        // Function to update the trust indicator message based on current count
        function updateTrustIndicatorMessage() {
            const trustIndicator = document.querySelector('.trust-indicator');
            if (!trustIndicator) return;
            
            let levelClass, icon, header, message;
            
            if (trustCount >= 1 && trustCount <= 4) {
                levelClass = 'trust-level-low';
                icon = 'fa-star-half-alt';
                header = 'Getting Started';
                message = 'A few users have interest in this vehicle owner.';
            } else if (trustCount >= 5 && trustCount <= 10) {
                levelClass = 'trust-level-medium';
                icon = 'fa-star';
                header = 'Growing Reputation';
                message = 'This owner is getting noticed in your area.';
            } else {
                levelClass = 'trust-level-highest';
                icon = 'fa-crown';
                header = 'Highly Trusted';
                message = 'Most trusted vehicle owner in your area.';
            }
            
            // Remove all level classes and add the current one
            trustIndicator.classList.remove('trust-level-low', 'trust-level-medium', 'trust-level-high', 'trust-level-highest');
            trustIndicator.classList.add(levelClass);
            
            // Update the icon
            const iconElement = trustIndicator.querySelector('.trust-indicator-header i');
            if (iconElement) {
                iconElement.className = `fas ${icon}`;
            }
            
            // Update the header text
            const headerElement = trustIndicator.querySelector('.trust-indicator-header span');
            if (headerElement) {
                headerElement.textContent = header;
            }
            
            // Update the message
            const messageElement = trustIndicator.querySelector('.trust-indicator-message');
            if (messageElement) {
                messageElement.textContent = message;
            }
            
            // Also update the counter text
            const counterElement = document.querySelector('.trust-counter-large span');
            if (counterElement) {
                counterElement.innerHTML = `<span id="trustCounter" class="trust-counter-number">${trustCount}</span> user${trustCount !== 1 ? 's' : ''} showed interest in this vehicle`;
            }
        }
        
        // Function to actually show the modal content once we have images
        function displayModalContent(images) {
            // Make sure images is an array and filter out invalid URLs
            if (!images) images = [];
            if (!Array.isArray(images)) images = [images];
            
            // Filter out invalid URLs and hidden folder markers
            images = images.filter(url => url && typeof url === 'string' && 
                !url.endsWith('.hidden_folder') && 
                !url.endsWith('.folder') && 
                url.trim() !== '');
            
            // Add default image if no images are provided
            if (images.length === 0) {
                images.push('attached_assets/images/default-vehicle.png');
            }
            
            // Check if the vehicle is newly registered (less than one month old)
            let isNewVehicle = false;
            let isVerified = false;
            
            if (registrationDate) {
                try {
                    const regDate = new Date(registrationDate);
                    const currentDate = new Date();
                    
                    // Calculate the difference in milliseconds
                    const diffTime = currentDate - regDate;
                    // Convert to days
                    const diffDays = Math.floor(diffTime / (1000 * 60 * 60 * 24));
                    
                    // If less than 30 days, it's a new vehicle
                    if (diffDays < 30) {
                        isNewVehicle = true;
                    } else {
                        // If more than 30 days, it's verified
                        isVerified = true;
                    }
                } catch (error) {
                    console.error('Error calculating vehicle age:', error);
                }
            }
            
            // Prepare badge HTML based on vehicle status
            let badgeHTML = '';
            let statusInfoHTML = '';
            
            if (isNewVehicle) {
                badgeHTML = `<div class="new-vehicle-badge" style="top: 20px; right: 20px; width: 50px; height: 50px;">
                    <img src="attached_assets/images/new (1).webp" alt="New Vehicle">
                </div>`;
                
                statusInfoHTML = `
                    <div class="modal-section" style="margin-top: 15px; background-color: #fff8e1; padding: 10px; border-radius: 20px;">
                        <div style="display: flex; align-items: center; gap: 10px;">
                            <img src="attached_assets/images/new (1).webp" alt="New Vehicle" style="width: 30px; height: 30px; object-fit: contain;">
                            <div>
                                <div style="font-weight: 600; color: #333;">New Vehicle Owner</div>
                                <div style="font-size: 14px; color: #666;">This owner recently registered their vehicle with us.</div>
                            </div>
                        </div>
                    </div>
                `;
            } else if (isVerified && !isPremiumForModal) {
                badgeHTML = `<div class="verified-badge" style="top: 20px; left: 20px; width: 50px; height: 50px;">
                    <img src="attached_assets/images/verify.webp" alt="Verified Vehicle">
                </div>`;
                
                statusInfoHTML = `
                    <div class="modal-section" style="margin-top: 15px; background-color: #e8f5e9; padding: 10px; border-radius: 8px; border-left: 4px solid #4caf50;">
                        <div style="display: flex; align-items: center; gap: 10px;">
                            <img src="attached_assets/images/verify.webp" alt="Verified Vehicle" style="width: 30px; height: 30px; object-fit: contain;">
                            <div>
                                <div style="font-weight: 600; color: #333;">Verified by Us</div>
                                <div style="font-size: 14px; color: #666;">This vehicle has been with us for over a month and has been verified.</div>
                            </div>
                        </div>
                    </div>
                `;
            }
            
            // We'll create document badges later after the modal HTML is populated
            
            // Populate modal header
            const headerImageSrc = safeUrl(images[0]) || 'attached_assets/images/default-vehicle.png';
            const vehicleNameForAlt = escapeHtml(vehicleName);
            modalHeader.innerHTML = `
                <img src="${headerImageSrc}" alt="${vehicleNameForAlt}" class="modal-header-image" onerror="this.src='attached_assets/images/default-vehicle.png'">
                ${badgeHTML}
            `;
            // Bind click to open full image
            const headerImg = modalHeader.querySelector('.modal-header-image');
            if (headerImg) {
                headerImg.style.cursor = 'zoom-in';
                headerImg.addEventListener('click', () => {
                    lightboxImage.src = headerImg.src;
                    imageLightbox.classList.add('active');
                    document.body.style.overflow = 'hidden';
                });
                // Keep default browser interactions enabled intentionally
            }
            
            // Populate gallery
            modalGallery.innerHTML = '';
            images.forEach((image, index) => {
                const galleryItem = document.createElement('div');
                galleryItem.className = 'gallery-item';
                galleryItem.style.position='relative';
                const sk = document.createElement('div');
                sk.className='gallery-skeleton';
                sk.style.width='100%'; sk.style.height='100%';
                galleryItem.appendChild(sk);
                const im = document.createElement('img');
                im.alt = `Vehicle image ${index + 1}`;
                im.loading = 'lazy';
                galleryItem.appendChild(im);
                const retryWrap = document.createElement('div');
                retryWrap.className='retry-wrapper hidden';
                retryWrap.innerHTML='<div>Image failed</div><div class="retry-icon" title="Retry"><i class="fas fa-redo"></i></div>';
                galleryItem.appendChild(retryWrap);
                retryWrap.querySelector('.retry-icon').addEventListener('click', () => {
                    retryWrap.classList.add('hidden');
                    loadModalImage(im, image, sk, retryWrap, 1);
                });
                modalGallery.appendChild(galleryItem);
                loadModalImage(im, image, sk, retryWrap, 0);
                galleryItem.addEventListener('click', function() {
                    const header = document.querySelector('.modal-header-image');
                    if (header && im.src) header.src = im.src;
                });
            });
            
            // Format service highlights
            let highlightsHTML = '';
            if (serviceHighlights && serviceHighlights.length > 0) {
                highlightsHTML = `
                    <ul class="modal-info-list">
                        ${serviceHighlights.map(highlight => `
                            <li class="modal-info-item">
                                <div class="info-icon"><i class="fas fa-check-circle"></i></div>
                                <div class="info-content">
                                    <div class="info-value">${highlight}</div>
                                </div>
                            </li>
                        `).join('')}
                    </ul>
                `;
            } else {
                highlightsHTML = '<p>No service highlights available</p>';
            }
            
            // Helper function to create contact info with copy button
            function createContactInfoWithCopy(value) {
                if (value === '-') return `<div class="info-value">${value}</div>`;
                
                return `
                    <div class="info-value-container">
                        <div class="info-value">${value}</div>
                        <i class="fas fa-copy copy-btn" title="Copy to clipboard" data-value="${value}"></i>
                    </div>
                `;
            }
            
            // Helper function to create contact info with copy and call buttons
            function createContactInfoWithCopyAndCall(value) {
                if (value === '-') return `<div class="info-value">${value}</div>`;
                
                return `
                    <div class="info-value-container">
                        <div class="info-value">${value}</div>
                        <i class="fas fa-copy copy-btn" title="Copy to clipboard" data-value="${value}"></i>
                    </div>
                `;
            }
            
            // Create trust counter HTML
            const trustCounterHTML = `
                <div class="trust-counter-large">
                    <i class="fas fa-shield-alt"></i>
                    <span><span id="trustCounter" class="trust-counter-number">${trustCount}</span> user${trustCount !== 1 ? 's' : ''} showed interest in this vehicle</span>
                </div>
            `;
            
            // Create trust indicator HTML with dynamic messaging based on trust count
            function getTrustIndicator() {
                let levelClass, icon, header, message;
                
                if (trustCount === 0) {
                    return ''; // No indicator for zero trust count
                } else if (trustCount >= 1 && trustCount <= 4) {
                    levelClass = 'trust-level-low';
                    icon = 'fa-star-half-alt';
                    header = 'Getting Started';
                    message = 'A few users have interest in this vehicle owner.';
                } else if (trustCount >= 5 && trustCount <= 10) {
                    levelClass = 'trust-level-medium';
                    icon = 'fa-star';
                    header = 'Growing Reputation';
                    message = 'This owner is getting noticed in your area.';
                } else {
                    levelClass = 'trust-level-highest';
                    icon = 'fa-crown';
                    header = 'Highly Trusted';
                    message = 'Most trusted vehicle owner in your area.';
                }
                
                return `
                    <div class="trust-indicator ${levelClass}">
                        <div class="trust-indicator-header">
                            <i class="fas ${icon}"></i>
                            <span>${header}</span>
                        </div>
                        <div class="trust-indicator-message">
                            ${message}
                        </div>
                    </div>
                `;
            }
            
            // Check if this is a manual cart
            const isManualCart = vehicleType && vehicleType.toLowerCase().includes('manual') || 
                                (registrationNumber && registrationNumber.startsWith('MANUAL-CART-'));
            
            // Create registration number section based on vehicle type
            let registrationNumberSection = '';
            if (!isManualCart) {
                registrationNumberSection = `
                    <li class="modal-info-item">
                        <div class="info-icon"><i class="fas fa-id-card"></i></div>
                        <div class="info-content">
                            <div class="info-label">Registration Number</div>
                            ${createContactInfoWithCopy(registrationNumber)}
                        </div>
                    </li>
                `;
            }
            
            // Populate vehicle info
            modalInfo.innerHTML = `
                <div class="left-column">
                    ${trustCount > 0 ? trustCounterHTML : ''}
                    ${getTrustIndicator()}
                    ${statusInfoHTML}
                    <div class="modal-section">
                        <h4 class="modal-section-title">Vehicle Details</h4>
                        <ul class="modal-info-list">
                            <li class="modal-info-item">
                                <div class="info-icon"><i class="fas fa-truck"></i></div>
                                <div class="info-content">
                                    <div class="info-label">Vehicle Type</div>
                                    <div class="info-value">${vehicleTypeEsc}</div>
                                </div>
                            </li>
                            ${registrationNumberSection}
                            <li class="modal-info-item">
                                <div class="info-icon"><i class="fas fa-map-marker-alt"></i></div>
                                <div class="info-content">
                                    <div class="info-label">Location</div>
                                    <div class="info-value">${vehicleCityEsc}, ${vehicleStateEsc}</div>
                                </div>
                            </li>
                            <li class="modal-info-item">
                                <div class="info-icon"><i class="fas fa-thumbtack"></i></div>
                                <div class="info-content">
                                    <div class="info-label">Pincode</div>
                                    <div class="info-value">${vehiclePincodeEsc}</div>
                                </div>
                            </li>
                        </ul>
                    </div>
                    <div class="modal-section">
                        <h4 class="modal-section-title"><i class="fas fa-cogs"></i> Service Highlights</h4>
                        ${highlightsHTML}
                    </div>
                </div>
                <div class="right-column">
                    <div class="modal-section">
                        <h4 class="modal-section-title">Owner Information</h4>
                        <ul class="modal-info-list">
                            <li class="modal-info-item">
                                <div class="info-icon"><i class="fas fa-user"></i></div>
                                <div class="info-content">
                                    <div class="info-label">Name</div>
                                    <div class="info-value">${ownerNameEsc}</div>
                                </div>
                            </li>
                            <li class="modal-info-item">
                                <div class="info-icon"><i class="fas fa-phone"></i></div>
                                <div class="info-content">
                                    <div class="info-label">Contact</div>
                                    ${createContactInfoWithCopy(ownerPhone)}
                                </div>
                            </li>
                            <li class="modal-info-item">
                                <div class="info-icon"><i class="fab fa-whatsapp"></i></div>
                                <div class="info-content" data-field="owner-whatsapp">
                                    <div class="info-label">WhatsApp</div>
                                    ${whatsappNumber ? createContactInfoWithCopy(whatsappNumber) : '<div class="info-value">WhatsApp number not provided by vehicle owner</div>'}
                                </div>
                            </li>
                            <li class="modal-info-item">
                                <div class="info-icon"><i class="fas fa-phone-alt"></i></div>
                                <div class="info-content" data-field="owner-alternate">
                                    <div class="info-label">Alternate Number</div>
                                    ${createContactInfoWithCopy(alternateNumber)}
                                </div>
                            </li>
                            ${registrationDate ? `
                            <li class="modal-info-item">
                                <div class="info-icon"><i class="fas fa-calendar-alt"></i></div>
                                <div class="info-content">
                                    <div class="info-label">Registered Since</div>
                                    <div class="info-value">${escapeHtml(formatDate(registrationDate))}</div>
                                </div>
                            </li>` : ''}
                        </ul>
                    </div>
                    
                    <!-- Document Verification Badges Section -->
                    <div class="document-badges-section" id="documentBadges">
                        <h3><i class="fas fa-award"></i> Document Verification</h3>
                        <div class="document-badges-container">
                            <!-- Badges will be populated by JavaScript based on uploaded documents -->
                        </div>
                    </div>
                </div>
            `;
            
            // Create and populate document badges
            const documentBadgesContainer = document.querySelector('.document-badges-container');
            if (documentBadgesContainer) {
                documentBadgesContainer.innerHTML = ''; // Clear previous badges
                
                // Check if RC document exists
                const rcBadge = document.createElement('div');
                rcBadge.className = 'document-badge-item';
                
                if (vehicle.rc) {
                    rcBadge.innerHTML = `
                        <div class="document-badge-icon">
                            <i class="fas fa-id-card"></i>
                        </div>
                        <div class="document-badge-info">
                            <div class="document-badge-title">Vehicle RC</div>
                            <div class="document-badge-status verified">
                                <i class="fas fa-check-circle"></i> Verified
                            </div>
                            <div class="document-badge-meaning">Trusted Owner Badge</div>
                        </div>
                    `;
                } else {
                    rcBadge.innerHTML = `
                        <div class="document-badge-icon">
                            <i class="fas fa-id-card"></i>
                        </div>
                        <div class="document-badge-info">
                            <div class="document-badge-title">Vehicle RC</div>
                            <div class="document-badge-status not-verified">
                                <i class="fas fa-times-circle"></i> Not Verified
                            </div>
                            <div class="document-badge-meaning">Registration Certificate not uploaded</div>
                        </div>
                    `;
                }
                
                // Check if Driver License document exists
                const dlBadge = document.createElement('div');
                dlBadge.className = 'document-badge-item';
                
                if (vehicle.d_l) {
                    dlBadge.innerHTML = `
                        <div class="document-badge-icon">
                            <i class="fas fa-id-badge"></i>
                        </div>
                        <div class="document-badge-info">
                            <div class="document-badge-title">Driver License</div>
                            <div class="document-badge-status verified">
                                <i class="fas fa-check-circle"></i> Verified
                            </div>
                            <div class="document-badge-meaning">Licensed Driver Badge</div>
                        </div>
                    `;
                } else {
                    dlBadge.innerHTML = `
                        <div class="document-badge-icon">
                            <i class="fas fa-id-badge"></i>
                        </div>
                        <div class="document-badge-info">
                            <div class="document-badge-title">Driver License</div>
                            <div class="document-badge-status not-verified">
                                <i class="fas fa-times-circle"></i> Not Verified
                            </div>
                            <div class="document-badge-meaning">Driver's License not uploaded</div>
                        </div>
                    `;
                }
                
                // Add badges to container
                documentBadgesContainer.appendChild(rcBadge);
                documentBadgesContainer.appendChild(dlBadge);
            }
            
            // Add event listeners to copy buttons
            modalInfo.querySelectorAll('.copy-btn').forEach(btn => {
                btn.addEventListener('click', function() {
                    const textToCopy = this.getAttribute('data-value');
                    navigator.clipboard.writeText(textToCopy)
                        .then(() => {
                            showToast('Copied to clipboard!', 'success');
                        })
                        .catch(err => {
                            console.error('Could not copy text: ', err);
                            showToast('Failed to copy text', 'error');
                        });
                });
            });
            
            // Set up contact button to use WhatsApp if available
            if (whatsappNumber && whatsappNumber !== '-' && whatsappNumber !== '') {
                contactDriverBtn.innerHTML = '<i class="fab fa-whatsapp"></i> Contact via WhatsApp';
                contactDriverBtn.onclick = function() {
                    const cleanNumber = whatsappNumber.replace(/\D/g, '');
                    const message = encodeURIComponent("Hello, I got your contact from HerapheriGoods. I want to know more about your vehicle service.\nनमस्ते, मुझे आपका संपर्क HerapheriGoods से मिला है। मैं आपकी वाहन सेवा के बारे में और जानना चाहता हूँ।");
                    window.open(`https://wa.me/${cleanNumber}?text=${message}`, '_blank');
                };
                contactDriverBtn.style.display = 'inline-block';
            } else {
                // Hide WhatsApp button if WhatsApp number is not available
                contactDriverBtn.style.display = 'none';
            }
            
            // Set up call button
            callDriverBtn.innerHTML = '<i class="fas fa-phone"></i> Call him';
            callDriverBtn.onclick = function() {
                // Update trust counter first
                updateTrustCounter();
                
                // Then initiate the call
                if (ownerPhone && ownerPhone !== '-') {
                    window.location.href = `tel:${ownerPhone}`;
                } else if (whatsappNumber && whatsappNumber !== '-') {
                    window.location.href = `tel:${whatsappNumber}`;
                } else if (alternateNumber && alternateNumber !== '-') {
                    window.location.href = `tel:${alternateNumber}`;
                } else {
                    showToast('Contact information not available', 'error');
                }
            };

            // After modal content is rendered, refresh document badges from backend to ensure accuracy
            try {
                if (vehicleId) {
                    const docsUrl = `${API_BASE_URL}registration/${vehicleId}/documents?_t=${Date.now()}`;
                    const paintBadges = (docs) => {
                        const rcUrl = docs.rc && docs.rc.url ? docs.rc.url : null;
                        const dlUrl = docs.dl && docs.dl.url ? docs.dl.url : null;
                        const container = document.querySelector('.document-badges-container');
                        if (!container) return;
                        container.innerHTML = '';
                        const rcBadge = document.createElement('div');
                        rcBadge.className = 'document-badge-item';
                        rcBadge.innerHTML = `
                            <div class="document-badge-icon"><i class="fas fa-id-card"></i></div>
                            <div class="document-badge-info">
                                <div class="document-badge-title">Vehicle RC</div>
                                <div class="document-badge-status ${rcUrl ? 'verified' : 'not-verified'}">
                                    <i class="fas ${rcUrl ? 'fa-check-circle' : 'fa-times-circle'}"></i> ${rcUrl ? 'Verified' : 'Not Verified'}
                                </div>
                                <div class="document-badge-meaning">${rcUrl ? 'Trusted Owner Badge' : 'Registration Certificate not uploaded'}</div>
                            </div>`;
                        const dlBadge = document.createElement('div');
                        dlBadge.className = 'document-badge-item';
                        dlBadge.innerHTML = `
                            <div class="document-badge-icon"><i class="fas fa-id-badge"></i></div>
                            <div class="document-badge-info">
                                <div class="document-badge-title">Driver License</div>
                                <div class="document-badge-status ${dlUrl ? 'verified' : 'not-verified'}">
                                    <i class="fas ${dlUrl ? 'fa-check-circle' : 'fa-times-circle'}"></i> ${dlUrl ? 'Verified' : 'Not Verified'}
                                </div>
                                <div class="document-badge-meaning">${dlUrl ? 'Licensed Driver Badge' : 'Driver\'s License not uploaded'}</div>
                            </div>`;
                        container.appendChild(rcBadge);
                        container.appendChild(dlBadge);
                    };
                    fetch(docsUrl)
                        .then(res => res.ok ? res.json() : Promise.reject(new Error(`HTTP ${res.status}`)))
                        .then(payload => {
                            const docs = payload && payload.documents ? payload.documents : {};
                            paintBadges(docs);
                            setTimeout(() => paintBadges(docs), 600);
                        })
                        .catch(err => console.warn('Failed to refresh document badges:', err));
                }
            } catch (e) {
                console.warn('Error scheduling document badge refresh', e);
            }
        }
        
        // Use our helper function to get images
        try {
            getImageUrlsForVehicle(vehicle, (images) => {
                try {
                    console.log('Got images for modal:', images);
                    displayModalContent(images);
                } catch (innerErr) {
                    console.error('Error rendering modal content:', innerErr);
                    // Fallback minimal content to ensure modal is usable
                    const fallbackTitle = vehicle.name || vehicle.vehicleName || vehicle.fullName || 'Vehicle Details';
                    if (modalHeader) {
                        modalHeader.innerHTML = `<div class="modal-header-overlay"><div class="modal-vehicle-title">${escapeHtml(fallbackTitle)}</div></div>`;
                    }
                    if (modalGallery) {
                        modalGallery.innerHTML = '<img src="attached_assets/images/default-vehicle.png" alt="Vehicle" style="width:100%;max-height:300px;object-fit:cover;">';
                    }
                    if (modalInfo) {
                        const vehicleType = vehicle.type || vehicle.vehicleType || 'Vehicle';
                        const vehicleCity = vehicle.locationCity || vehicle.city || 'Unknown';
                        const vehicleState = vehicle.locationState || vehicle.state || 'Unknown';
                        const ownerName = vehicle.ownerName || vehicle.driverName || vehicle.owner || vehicle.fullName || 'Owner';
                        const vehicleTypeEsc = escapeHtml(vehicleType);
                        const vehicleCityEsc = escapeHtml(vehicleCity);
                        const vehicleStateEsc = escapeHtml(vehicleState);
                        const ownerNameEsc = escapeHtml(ownerName);
                        modalInfo.innerHTML = `
                            <div class="left-column">
                                <div class="modal-section">
                                    <h4 class="modal-section-title">Vehicle Details</h4>
                                    <ul class="modal-info-list">
                                        <li class="modal-info-item"><div class="info-icon"><i class="fas fa-truck"></i></div><div class="info-content"><div class="info-label">Vehicle Type</div><div class="info-value">${vehicleTypeEsc}</div></div></li>
                                        <li class="modal-info-item"><div class="info-icon"><i class="fas fa-map-marker-alt"></i></div><div class="info-content"><div class="info-label">Location</div><div class="info-value">${vehicleCityEsc}, ${vehicleStateEsc}</div></div></li>
                                    </ul>
                                </div>
                            </div>
                            <div class="right-column">
                                <div class="modal-section">
                                    <h4 class="modal-section-title">Owner Information</h4>
                                    <ul class="modal-info-list">
                                        <li class="modal-info-item"><div class="info-icon"><i class="fas fa-user"></i></div><div class="info-content"><div class="info-label">Name</div><div class="info-value">${ownerNameEsc}</div></div></li>
                                    </ul>
                                </div>
                            </div>`;
                    }
                }
            });
        } catch (err) {
            console.error('Unexpected error while preparing images:', err);
        }
    }

    // Modal image loader with retry attempts
    function loadModalImage(imgEl, url, skeletonEl, retryWrap, attempt=0) {
        const MAX_ATTEMPTS = 2;
        const safe = safeUrl(url);
        if (!safe) {
            if (skeletonEl) skeletonEl.remove();
            retryWrap.classList.remove('hidden');
            return;
        }
        const tester = new Image();
        tester.onload = () => {
            imgEl.src = safe;
            if (skeletonEl) skeletonEl.remove();
            imgEl.style.opacity='0';
            requestAnimationFrame(()=>{ imgEl.style.transition='opacity .4s'; imgEl.style.opacity='1'; });
        };
        tester.onerror = () => {
            if (attempt < MAX_ATTEMPTS) {
                setTimeout(()=> loadModalImage(imgEl, safe, skeletonEl, retryWrap, attempt+1), 900);
            } else {
                if (skeletonEl) skeletonEl.remove();
                retryWrap.classList.remove('hidden');
            }
        };
        tester.src = safe;
    }
    
    // Function to format date
    function formatDate(dateString) {
        if (!dateString) return 'Unknown';
        
        try {
        const options = { year: 'numeric', month: 'long', day: 'numeric' };
        return new Date(dateString).toLocaleDateString(undefined, options);
        } catch (e) {
            console.error('Error formatting date:', e);
            return dateString; // Return the original string if there's an error
        }
    }
    
    // Close modal
    modalClose.addEventListener('click', closeModal);
    
    // Close modal if clicked outside of content
    vehicleModal.addEventListener('click', function(e) {
        if (e.target === vehicleModal) {
            closeModal();
        }
    });

    // Lightbox close handlers
    if (closeLightbox) {
        closeLightbox.addEventListener('click', () => {
            imageLightbox.classList.remove('active');
            lightboxImage.src = '';
            document.body.style.overflow = 'auto';
        });
    }
    if (imageLightbox) {
        imageLightbox.addEventListener('click', (e) => {
            if (e.target === imageLightbox) {
                imageLightbox.classList.remove('active');
                lightboxImage.src = '';
                document.body.style.overflow = 'auto';
            }
        });
    }
    
    // Function to close modal
    function closeModal() {
        vehicleModal.style.display = 'none';
        document.body.style.overflow = 'auto';
    }
    
    // Remove previous refetch-on-sort logic; rely on in-memory resort only
    // Pagination controls rendering
    function updatePaginationControls() {
        const container = document.getElementById('paginationControls');
        if (!container) return;
        container.innerHTML = '';
        if (totalPages <= 1) return;

        container.setAttribute('role', 'navigation');
        container.classList.add('pagination-controls');

        const makeBtn = (label, ariaLabel, disabled, onClick, isActive=false, type='button') => {
            const btn = document.createElement('button');
            btn.type = type;
            btn.textContent = label;
            btn.className = 'pagination-btn';
            if (isActive) btn.classList.add('active');
            if (disabled) btn.disabled = true;
            if (ariaLabel) btn.setAttribute('aria-label', ariaLabel);
            btn.addEventListener('click', (e) => { e.preventDefault(); if (!btn.disabled) onClick(); });
            return btn;
        };

        // First & Prev
        container.appendChild(makeBtn('«', 'First page', currentPage === 1, () => { currentPage = 1; refetchLast(); }));
        container.appendChild(makeBtn('‹', 'Previous page', currentPage === 1, () => { currentPage--; refetchLast(); }));

        // Page number logic with ellipsis
        const pages = [];
        if (totalPages <= 9) {
            for (let p = 1; p <= totalPages; p++) pages.push(p);
        } else {
            pages.push(1);
            const windowStart = Math.max(2, currentPage - 2);
            const windowEnd = Math.min(totalPages - 1, currentPage + 2);
            if (windowStart > 2) pages.push('ellipsis-left');
            for (let p = windowStart; p <= windowEnd; p++) pages.push(p);
            if (windowEnd < totalPages - 1) pages.push('ellipsis-right');
            pages.push(totalPages);
        }

        pages.forEach(p => {
            if (typeof p === 'number') {
                container.appendChild(makeBtn(String(p), `Page ${p}`, false, () => { if (p !== currentPage) { currentPage = p; refetchLast(); } }, p === currentPage));
            } else {
                const span = document.createElement('span');
                span.className = 'pagination-ellipsis';
                span.textContent = '…';
                span.setAttribute('aria-hidden', 'true');
                container.appendChild(span);
            }
        });

        // Next & Last
        container.appendChild(makeBtn('›', 'Next page', currentPage === totalPages, () => { currentPage++; refetchLast(); }));
        container.appendChild(makeBtn('»', 'Last page', currentPage === totalPages, () => { currentPage = totalPages; refetchLast(); }));
    }
    function refetchLast() {
        if (!lastSearchCriteria) {
            // fallback to initial load
            initialLoadAllVehicles();
            return;
        }
        const { vehicleType, state, city, pincode } = lastSearchCriteria;
        fetchVehiclesFromDatabase(vehicleType, state, city, pincode);
    }
    
    // Update Find Vehicle button in index
    document.querySelector('.find-vehicle-btn')?.addEventListener('click', function() {
        if (typeof window.buildUrl === 'function') {
            window.location.href = window.buildUrl('vehicles');
        } else {
            window.location.href = 'vehicles';
        }
    });

    // Initial state setup - ensure the vehicle list is empty on page load
    if (vehiclesGrid) {
        vehiclesGrid.innerHTML = '';
    }
    
    // Update results count
    if (resultsCount) {
        resultsCount.textContent = 'Showing 0 vehicles';
    }
    
    // Show no results message
    if (noResults) {
        noResults.style.display = 'block';
    }
    
    // Log this setup for debugging
    console.log('Initial state set - no vehicles displayed until search');

    // Global variables for searches
    let lastSearchCriteria = null; // Store last search criteria
    let searchResults = []; // Store search results
    
    // Remove refresh page when refresh button is clicked
    const refreshBtn = document.getElementById('refresh-page');
    if (refreshBtn) {
        refreshBtn.style.display = 'none'; // Hide the refresh button instead of setting up the event
    }
    
    // Initialize any needed event listeners
    // (Second duplicate listener removed above)
});