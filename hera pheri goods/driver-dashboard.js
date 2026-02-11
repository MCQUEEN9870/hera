// Driver Dashboard JavaScript

document.addEventListener('DOMContentLoaded', function() {
    let isForcedLogoutInProgress = false;

    function clearAuthStorage() {
        localStorage.removeItem('isLoggedIn');
        localStorage.removeItem('userPhone');
        localStorage.removeItem('userMembership');
        if (window.AuthToken && typeof window.AuthToken.clear === 'function') {
            window.AuthToken.clear();
        } else {
            try { sessionStorage.removeItem('authToken'); } catch (_e) {}
            localStorage.removeItem('authToken');
        }
        localStorage.removeItem('lastSelectedVehicleId');
    }

    function forceLogout(reason) {
        if (isForcedLogoutInProgress) return;
        isForcedLogoutInProgress = true;
        clearAuthStorage();
        const next = reason ? `login?reason=${encodeURIComponent(reason)}` : 'login';
        window.location.href = next;
    }

    function maybeForceLogoutOnAuthError(response) {
        if (!response) return false;
        const status = response.status;

        // 401/403 => token missing/expired/invalid. 404 on /api/users/{phone} => account deleted.
        if (status === 401 || status === 403) {
            forceLogout('session_expired');
            return true;
        }
        if (status === 404) {
            forceLogout('account_deleted');
            return true;
        }
        return false;
    }

    // Escape user-controlled strings before inserting into HTML.
    function escapeHtml(value) {
        return String(value ?? '')
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;')
            .replace(/'/g, '&#39;');
    }

    function renderUserNameWithBadge(name, membership) {
        const el = document.querySelector('.user-name');
        if (!el) return;

        el.textContent = '';
        el.append(document.createTextNode(name || 'Unknown User'));

        const badge = document.createElement('span');
        badge.className = 'membership-badge';
        badge.id = 'membershipBadge';
        badge.textContent = membership || 'Standard';
        el.append(' ');
        el.appendChild(badge);
    }
    // Network error handling function
    function handleNetworkError(errorMessage = "Could not connect to server. Please check your internet connection and try again.") {
        // Create error modal if it doesn't exist
        if (!document.getElementById('networkErrorModal')) {
            const errorModal = document.createElement('div');
            errorModal.id = 'networkErrorModal';
            errorModal.className = 'modal';
            errorModal.innerHTML = `
                <div class="modal-content network-error">
                    <div class="modal-header">
                        <span class="close">&times;</span>
                        <h2>Connection Error</h2>
                    </div>
                    <div class="modal-body">
                        <p id="networkErrorMessage">${errorMessage}</p>
                    </div>
                    <div class="modal-footer">
                        <button id="retryButton" class="ok-btn">OK</button>
                    </div>
                </div>
            `;
            document.body.appendChild(errorModal);
            
            // Add event listeners
            document.querySelector('#networkErrorModal .close').addEventListener('click', () => {
                document.getElementById('networkErrorModal').style.display = 'none';
            });
            
            document.getElementById('retryButton').addEventListener('click', () => {
                document.getElementById('networkErrorModal').style.display = 'none';
            });
        } else {
            // Update error message if modal already exists
            document.getElementById('networkErrorMessage').textContent = errorMessage;
        }
        
        // Show the error modal
        document.getElementById('networkErrorModal').style.display = 'block';
    }

    // Add retry functionality to fetch requests
    // Important: only treat *network* failures as "Connection Error".
    // HTTP errors (401/403/404/500) mean the server responded and should be handled by callers.
    async function fetchWithRetry(url, options = {}, retries = 3) {
        try {
            const response = await fetch(url, options);

            // Retry only on transient server errors.
            if (response.status >= 500 && retries > 0) {
                await new Promise(resolve => setTimeout(resolve, 1000));
                return fetchWithRetry(url, options, retries - 1);
            }

            return response;
        } catch (error) {
            if (retries > 0) {
                await new Promise(resolve => setTimeout(resolve, 1000));
                return fetchWithRetry(url, options, retries - 1);
            }

            // Only show connection modal when the request could not be made at all.
            handleNetworkError('Could not connect to server. Please check your internet connection and try again.');
            throw error;
        }
    }
    // Check authentication first
    checkAuth();
    
    // User data object that will be populated from API
    let userData = {
        name: "",
        contact: "",
        email: "",
        membership: "Standard", // Default to Standard
        maxVehicles: 3, // Default: 3 for Standard, 5 for Premium
        joinDate: "",
        profilePhoto: null, // Will store the profile photo URL when uploaded
        membershipPurchaseTime: null, // Will store the purchase date for premium users
        membershipExpireTime: null, // Will store the expiry date for premium users
        membershipPlan: "", // Will store the plan type (monthly, quarterly, etc.)
        membershipPrice: "" // Will store the plan price
    };
    // Expose for admin helpers defined outside this scope
    window.userData = userData;

    // Vehicle data array that will be populated from API
    let vehicles = [];
    
    // Base API URL - ensure it has a trailing slash
    const API_BASE_URL = (window.API_BASE_URL ? `${window.API_BASE_URL}/api/` : "http://localhost:8080/api/");
    
    // Helper function to ensure consistent API URL formatting
    function getApiUrl(path) {
        // Remove leading slash from path if it exists
        const cleanPath = path.startsWith('/') ? path.substring(1) : path;
        // Add cache-busting parameter to prevent browser caching
        const url = `${API_BASE_URL}${cleanPath}`;
        return url + (url.includes('?') ? '&' : '?') + '_t=' + new Date().getTime();
    }
    
    // Enable mock data for testing (set to false when backend is ready)
    const USE_MOCK_DATA = false;
    
    // Mock data for testing
    // const MOCK_USER = {
    //     fullName: localStorage.getItem('userPhone') || "User",
    //     contactNumber: localStorage.getItem('userPhone') || "9870907586",
    //     email: "user@example.com",
    //     membership: "Standard",
    //     joinDate: "2024-06-01"
    // };
    
    // const MOCK_VEHICLES = [
    //     {
    //         id: "MH12AB1234",
    //         number: "MH12 AB 1234",
    //         type: "Tata Ace (Chhota Hathi)",
    //         status: "active",
    //         owner: MOCK_USER.fullName,
    //         registrationDate: "15 Jan 2024",
    //         contact: MOCK_USER.contactNumber,
    //         whatsapp: MOCK_USER.contactNumber,
    //         location: "Mumbai, Maharashtra",
    //         pincode: "400001"
    //     },
    //     {
    //         id: "DL01CD5678",
    //         number: "DL01 CD 5678",
    //         type: "Mini Truck (Eicher Canter)",
    //         status: "active",
    //         owner: MOCK_USER.fullName,
    //         registrationDate: "20 Feb 2024",
    //         contact: MOCK_USER.contactNumber,
    //         whatsapp: MOCK_USER.contactNumber,
    //         location: "Delhi, Delhi",
    //         pincode: "110001"
    //     }
    // ];
    
    // Get user phone for API calls: prefer localStorage; allow ?phone=... for testing without login
    const userPhone = localStorage.getItem('userPhone') || new URLSearchParams(location.search).get('phone');
    
    // Check if user is authenticated
    function checkAuth() {
        const isLoggedIn = localStorage.getItem('isLoggedIn') === 'true';
        const token = (window.AuthToken && typeof window.AuthToken.get === 'function')
            ? window.AuthToken.get()
            : (function() {
                try { return sessionStorage.getItem('authToken'); } catch (_e) { return null; }
            })();
        const phone = localStorage.getItem('userPhone');
        if (!isLoggedIn) {
            // If not logged in, redirect to login page
            alert('Please login to access the dashboard');
            window.location.href = 'login';
            return false;
        }
        if (!token || !phone) {
            forceLogout('session_invalid');
            return false;
        }
        return true;
    }

    // Initialize user information in the UI
    function initUserInfo() {
        // Get user phone from localStorage
        const userPhone = localStorage.getItem('userPhone');
        
        if (userPhone) {
            // Show loading state
            document.querySelector('.user-name').innerHTML = 
                `<i class="fas fa-spinner fa-spin"></i> Loading...`;
            document.querySelector('.user-contact').textContent = '';
            
            if (USE_MOCK_DATA) {
                // Use mock data instead of API call
                setTimeout(() => {
                    // Update userData object with mock data
                    userData = {
                        name: MOCK_USER.fullName,
                        contact: MOCK_USER.contactNumber,
                        email: MOCK_USER.email,
                        membership: MOCK_USER.membership,
                        maxVehicles: MOCK_USER.membership === "Premium" ? 5 : 3,
                        joinDate: MOCK_USER.joinDate,
                        profilePhoto: null
                    };
                    
                    // Update UI with user data
                    renderUserNameWithBadge(userData.name, userData.membership);
                    document.querySelector('.user-contact').textContent = userData.contact;
                    try { setupEmailSettingsSection(); } catch (e) { console.warn('Email section init failed', e); }
                    
                    
                    // Also fetch vehicles data after user data is loaded
                    fetchVehiclesData();
                    
                    // Toggle premium sections visibility
                    togglePremiumSections();
                }, 500); // Simulate network delay
                return;
            }
            
            // Real API call when mock is disabled
            fetch(getApiUrl(`users/${userPhone}`), {
                method: 'GET',
                headers: {
                    'Content-Type': 'application/json'
                }
            })
            .then(response => {
                if (!response.ok) {
                    if (maybeForceLogoutOnAuthError(response)) {
                        throw new Error('Auth invalid; redirecting');
                    }
                    throw new Error('Failed to fetch user data');
                }
                return response.json();
            })
            .then(data => {
                // Avoid logging PII to the console
                
                // Update userData object with fetched data - Fix data structure handling
                    userData = {
                    name: data.user ? data.user.fullName : (data.fullName || data.name || "Unknown User"),
                    contact: data.user ? data.user.contactNumber : (data.contactNumber || data.phone || userPhone),
                    email: data.user ? data.user.email : (data.email || ''),
                    membership: data.user ? (data.user.membership || "Standard") : (data.membership || "Standard"),
                    maxVehicles: (data.user?.membership || data.membership) === "Premium" ? 5 : 3,
                    joinDate: data.user ? data.user.joinDate : (data.joinDate || new Date().toISOString().split('T')[0]),
                    profilePhoto: data.user ? (data.user.profilePhoto || data.user.profilePhotoUrl) : (data.profilePhotoUrl || null),
                    membershipPurchaseTime: data.user ? data.user.membershipPurchaseTime : (data.membershipPurchaseTime || null),
                    membershipExpireTime: data.user ? data.user.membershipExpireTime : (data.membershipExpireTime || null),
                    membershipPlan: data.user ? data.user.membershipPlan : (data.membershipPlan || ""),
                    membershipPrice: data.user ? data.user.membershipPrice : (data.membershipPrice || "")
                    };
                    
                    // Check if premium membership is expired
                    if (userData.membership === "Premium" && userData.membershipExpireTime) {
                        const currentTime = new Date();
                        const expiryTime = new Date(userData.membershipExpireTime);
                        
                        if (currentTime > expiryTime) {
                            console.log("Premium membership expired, downgrading to Standard");
                            userData.membership = "Standard";
                            userData.maxVehicles = 3;
                            
                            // Update the membership status on the server
                            updateExpiredMembershipOnServer();
                        }
                    }
                    
                    // Update UI with user data
                    renderUserNameWithBadge(userData.name, userData.membership);
                    document.querySelector('.user-contact').textContent = userData.contact;
                    
                    // Update the badge color based on membership
                    const membershipBadge = document.getElementById('membershipBadge');
                    if (membershipBadge) {
                        membershipBadge.style.backgroundColor = userData.membership === "Premium" ? '#FFD700' : '#0d6efd';
                    }
                    
                // Check if we have a profile photo URL
                    if (userData.profilePhoto) {
                    updateProfilePhotoUI(userData.profilePhoto);
                    }
                    
                    // Avoid logging PII to the console
                    
                    // Also fetch vehicles data after user data is loaded
                    fetchVehiclesData();
                    
                    // Toggle premium sections visibility
                    togglePremiumSections();
            })
            .catch(error => {
                if (isForcedLogoutInProgress) return;
                console.error('Error fetching user data:', error);
                
                // Show error state in user info area
                renderUserNameWithBadge('Unknown User', 'Error');
                document.querySelector('.user-contact').textContent = userPhone;
                
                // Show error state for vehicles
                showErrorState('Connection Error', 'Could not connect to the server to load your vehicles. Please check your internet connection.');
                
                showToast('Failed to load user data', 'error');
            })
            .finally(() => {
                // Update vehicle limit notice regardless of success/failure
                updateVehicleLimitNotice();
            });
        } else {
            // No user phone in localStorage, redirect to login
            console.error('No user phone found in localStorage');
            window.location.href = 'login';
        }
    }

    // Toggle visibility of premium sections based on membership status
    function togglePremiumSections() {
        const premiumUpgradeSection = document.getElementById('premiumUpgradeSection');
        const myPlansSection = document.getElementById('myPlansSection');
        
        if (!premiumUpgradeSection || !myPlansSection) {
            console.error('Premium sections not found in DOM');
            return;
        }
        
        if (userData.membership === 'Premium') {
            // For Premium users: Hide upgrade section, show plans section
            premiumUpgradeSection.style.display = 'none';
            myPlansSection.style.display = 'block';
            
            // Update My Plans & Pricing section with user's membership details
            updateMyPlansSection();
        } else {
            // For Standard users: Show upgrade section, hide plans section
            premiumUpgradeSection.style.display = 'block';
            myPlansSection.style.display = 'none';
        }
    }

    // Email Add/Change Section logic
    function setupEmailSettingsSection() {
        const section = document.getElementById('emailSettingsSection');
        if (!section) return;
        const emailInput = document.getElementById('emailInput');
        const saveBtn = document.getElementById('saveEmailBtn');
        const editBtn = document.getElementById('editEmailBtn');
        const title = document.getElementById('emailSectionTitle');
        const subtitle = document.getElementById('emailSectionSubtitle');
        const statusMsg = document.getElementById('emailStatusMsg');

        // Helper to re-fetch user data and refresh UI
        async function refetchUser() {
            const phone = localStorage.getItem('userPhone');
            if (!phone) return;
            try {
                const res = await fetch(getApiUrl(`users/${phone}`));
                if (!res.ok) return;
                const data = await res.json();
                userData.email = data?.user?.email || '';
            } catch (e) {}
        }

        const currentEmail = (userData && userData.email) ? String(userData.email).trim() : '';
        if (currentEmail) {
            emailInput.value = currentEmail;
            emailInput.disabled = true;
            saveBtn.style.display = 'none';
            editBtn.style.display = '';
            title.textContent = 'Email on file';
            subtitle.textContent = 'You can change your email anytime';
            statusMsg.textContent = '';
        } else {
            emailInput.value = '';
            emailInput.disabled = false;
            saveBtn.style.display = '';
            editBtn.style.display = 'none';
            title.textContent = 'Add your email';
            subtitle.textContent = 'Add an email to receive booking updates and notifications';
            statusMsg.textContent = '';
        }

        if (editBtn) {
            editBtn.onclick = () => {
                emailInput.disabled = false;
                emailInput.focus();
                saveBtn.style.display = '';
                editBtn.style.display = 'none';
                statusMsg.textContent = '';
            };
        }

        if (saveBtn) {
            saveBtn.onclick = async () => {
                const email = String(emailInput.value || '').trim();
                statusMsg.style.color = '#666';
                if (!email) {
                    statusMsg.style.color = '#d32f2f';
                    statusMsg.textContent = 'Please enter your email address';
                    showToast('Please enter your email address', 'error');
                    return;
                }
                const re = /^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$/;
                if (!re.test(email)) {
                    statusMsg.style.color = '#d32f2f';
                    statusMsg.textContent = 'Please enter a valid email address';
                    showToast('Please enter a valid email address', 'error');
                    return;
                }

                const phone = localStorage.getItem('userPhone');
                if (!phone) {
                    statusMsg.style.color = '#d32f2f';
                    statusMsg.textContent = 'User not logged in. Please login again.';
                    showToast('Please login again', 'error');
                    return;
                }

                saveBtn.disabled = true;
                saveBtn.innerText = 'Saving...';
                try {
                    const resp = await fetch(getApiUrl(`users/${phone}/email`), {
                        method: 'PUT',
                        headers: { 'Content-Type': 'application/json' },
                        body: JSON.stringify({ email })
                    });
                    const data = await resp.json().catch(() => ({ success: false, message: 'Unexpected response' }));
                    if (!resp.ok || !data.success) {
                        throw new Error(data.message || `Failed with status ${resp.status}`);
                    }
                    userData.email = email;
                    emailInput.value = email;
                    emailInput.disabled = true;
                    saveBtn.style.display = 'none';
                    if (editBtn) editBtn.style.display = '';
                    statusMsg.style.color = '#2e7d32';
                    statusMsg.textContent = 'Email saved successfully';
                    showToast('Email saved successfully', 'success');
                    await refetchUser();
                } catch (err) {
                    statusMsg.style.color = '#d32f2f';
                    statusMsg.textContent = String(err.message || err);
                    showToast(String(err.message || 'Failed to save email'), 'error');
                } finally {
                    saveBtn.disabled = false;
                    saveBtn.innerText = 'Save';
                }
            };
        }
    }
    
    // Update My Plans & Pricing section with user's membership details
    function updateMyPlansSection() {
        // Only proceed if the user is premium
        if (userData.membership !== 'Premium') return;
        
        // Get elements
        const planTypeElement = document.getElementById('planType');
        const planPriceElement = document.getElementById('planPrice');
        const purchaseDateElement = document.getElementById('purchaseDate');
        const expiryDateElement = document.getElementById('expiryDate');
        const daysRemainingElement = document.getElementById('daysRemaining');
        const expiryProgressBar = document.getElementById('expiryProgressBar');
        
        // Format dates
        let purchaseDate = 'Not available';
        let expiryDate = 'Not available';
        let daysRemaining = 0;
        let progressPercentage = 0;
        let planType = 'Premium';
        let planPrice = '';
        let isExpired = false;
        
        // Calculate days remaining and progress
        if (userData.membershipPurchaseTime && userData.membershipExpireTime) {
            try {
                // Parse dates
                const purchaseDateTime = new Date(userData.membershipPurchaseTime);
                const expiryDateTime = new Date(userData.membershipExpireTime);
                const currentTime = new Date();
                
                // Check if membership is expired
                isExpired = currentTime > expiryDateTime;
                
                // Format dates for display
                purchaseDate = purchaseDateTime.toLocaleDateString('en-IN', {
                    day: 'numeric',
                    month: 'short',
                    year: 'numeric'
                });
                
                expiryDate = expiryDateTime.toLocaleDateString('en-IN', {
                    day: 'numeric',
                    month: 'short',
                    year: 'numeric'
                });
                
                // Calculate days remaining
                const msPerDay = 1000 * 60 * 60 * 24;
                const totalDays = Math.round((expiryDateTime - purchaseDateTime) / msPerDay);
                daysRemaining = Math.max(0, Math.round((expiryDateTime - currentTime) / msPerDay));
                
                // Calculate progress percentage (how much time has passed)
                const elapsed = currentTime - purchaseDateTime;
                const total = expiryDateTime - purchaseDateTime;
                progressPercentage = Math.min(100, Math.max(0, Math.round((elapsed / total) * 100)));
                
                // Invert the percentage to show remaining time instead of elapsed time
                progressPercentage = 100 - progressPercentage;
                
                // Determine plan type and price based on the time gap between purchase and expiry dates
                const monthsGap = Math.round(totalDays / 30);
                
                if (monthsGap <= 1) {
                    planType = 'Monthly';
                    planPrice = '59';
                } else if (monthsGap <= 3) {
                    planType = 'Quarterly';
                    planPrice = '159';
                } else if (monthsGap <= 6) {
                    planType = 'Half-Yearly';
                    planPrice = '299';
                } else {
                    planType = 'Yearly';
                    planPrice = '499';
                }
                
                // Store the calculated plan type and price in userData
                userData.membershipPlan = planType;
                userData.membershipPrice = planPrice;
            } catch (e) {
                console.error('Error calculating membership dates:', e);
            }
        }
        
        // Update UI elements
        if (planTypeElement) {
            planTypeElement.textContent = userData.membershipPlan || planType;
        }
        
        if (planPriceElement) {
            planPriceElement.textContent = userData.membershipPrice ? `₹${userData.membershipPrice}` : (planPrice ? `₹${planPrice}` : 'Not available');
        }
        
        if (purchaseDateElement) {
            purchaseDateElement.textContent = purchaseDate;
        }
        
        if (expiryDateElement) {
            expiryDateElement.textContent = expiryDate;
        }
        
        if (daysRemainingElement) {
            daysRemainingElement.textContent = isExpired ? 'Expired' : `${daysRemaining} days left`;
            
            // Change color based on days remaining or if expired
            if (isExpired) {
                daysRemainingElement.style.color = '#dc3545'; // Red for expired
            } else if (daysRemaining <= 7) {
                daysRemainingElement.style.color = '#dc3545'; // Red for last week
            } else if (daysRemaining <= 30) {
                daysRemainingElement.style.color = '#ffc107'; // Yellow for last month
            }
        }
        
        if (expiryProgressBar) {
            expiryProgressBar.style.width = isExpired ? '0%' : `${progressPercentage}%`;
            
            // Change color based on progress
            if (isExpired || progressPercentage <= 15) {
                expiryProgressBar.style.background = 'linear-gradient(90deg, #dc3545, #ff6b6b)'; // Red gradient for expired or < 15% remaining
            } else if (progressPercentage <= 30) {
                expiryProgressBar.style.background = 'linear-gradient(90deg, #ffc107, #ffdd33)'; // Yellow gradient for < 30% remaining
            }
        }
        
        // If membership is expired, update the membership status to Standard
        if (isExpired) {
            // Update local user data
            userData.membership = 'Standard';
            userData.maxVehicles = 3;
            
            // Update UI
            const membershipBadge = document.getElementById('membershipBadge');
            if (membershipBadge) {
                membershipBadge.textContent = 'Standard';
                membershipBadge.style.backgroundColor = '#0d6efd'; // Blue for Standard
            }
            
            // Update vehicle limit notice
            updateVehicleLimitNotice();
            
            // Toggle premium sections visibility
            premiumUpgradeSection.style.display = 'block';
            myPlansSection.style.display = 'none';
            
            // Save standard status to localStorage
            localStorage.setItem('userMembership', 'Standard');
            
            // Show notification about expired membership
            showToast('Your Premium membership has expired. You have been downgraded to Standard plan.', 'warning', true);
            
            // Update the membership status on the server
            updateExpiredMembershipOnServer();
        }
    }
    
    // Update expired membership status on server
    function updateExpiredMembershipOnServer() {
        // Get user phone from localStorage
        const userPhone = localStorage.getItem('userPhone');
        if (!userPhone) return;
        
        // Make API call to update membership
        fetch(getApiUrl(`users/${userPhone}/downgrade-to-standard`), {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            }
        })
        .then(response => {
            if (!response.ok) {
                throw new Error(`Server responded with status: ${response.status}`);
            }
            return response.json();
        })
        .then(data => {
            console.log("Membership downgrade response:", data);
        })
        .catch(error => {
            console.error("Error updating expired membership status:", error);
        });
    }

    // Update profile photo in the UI
    async function updateProfilePhoto(photoURL) {
        try {
            const userAvatar = document.getElementById('userAvatar');
            const defaultAvatar = document.getElementById('defaultAvatar');
            
            // If photoURL is a File object, upload it first
            if (photoURL instanceof File) {
                // Show loading indicator
                const loadingOverlay = document.createElement('div');
                loadingOverlay.className = 'loading-overlay';
                loadingOverlay.innerHTML = `
                    <div class="loading-spinner"></div>
                    <p>Uploading photo...</p>
                `;
                document.body.appendChild(loadingOverlay);
                loadingOverlay.style.display = 'flex';
                
                // Create FormData for file upload
                // Backend expects @RequestParam("photo")
                const formData = new FormData();
                formData.append('photo', photoURL);
                
                // Get user phone number from localStorage
                const userPhone = localStorage.getItem('userPhone');
                
                try {
                    // Check if API is reachable first
                    const apiReachable = await isApiReachable();
                    if (!apiReachable) {
                        throw new Error('Could not connect to server');
                    }
                    
                    // Use fetchWithRetry instead of regular fetch
                    const response = await fetchWithRetry(getApiUrl(`users/${userPhone}/profile-photo`), {
                        method: 'POST',
                        body: formData
                    }, 3);
                    
                    const data = await response.json();
                    
                    if (data.success) {
                        // Update UI with the photo URL from the server
                        updateProfilePhoto(data.photoUrl);
                        photoModal.classList.remove('active');
                        resetPhotoPreview();
                        showToast('Profile photo updated successfully!');
                    } else {
                        throw new Error(data.message || 'Failed to upload photo');
                    }
                } catch (error) {
                    console.error('Error uploading photo:', error);
                    
                    // If server is unreachable, create and use local preview temporarily
                    if (error.message.includes('connect to server')) {
                        // Use FileReader to get local data URL
                        const reader = new FileReader();
                        reader.onload = function(e) {
                            const localPhotoUrl = e.target.result;
                            
                            // Store in local storage for temporary use
                            try {
                                localStorage.setItem('tempProfilePhoto', localPhotoUrl);
                            } catch (e) {
                                console.warn('Could not save to localStorage - likely too large');
                            }
                            
                            // Update UI with local image
                            updateProfilePhotoUI(localPhotoUrl);
                            
                            // Hide loading overlay
                            loadingOverlay.style.display = 'none';
                            document.body.removeChild(loadingOverlay);
                            
                            // Show warning about temporary nature
                            showToast('Photo saved temporarily. Will be fully uploaded when server is available.', 'warning', true);
                            photoModal.classList.remove('active');
                        };
                        reader.readAsDataURL(photoURL);
                        return;
                    }
                    
                    let errorMessage = 'Failed to upload photo. ';
                    
                    if (error.message.includes('NetworkError') || 
                        error.message.includes('Failed to fetch') ||
                        error.message.includes('Network request failed')) {
                        errorMessage += 'Could not connect to server. Please check your internet connection and try again.';
                    } else if (error.message.includes('status: 403') || 
                               error.message.includes('Forbidden')) {
                        errorMessage += 'Server permission error. Please try again later.';
                    } else if (error.message.includes('CORS')) {
                        errorMessage += 'Cross-origin request blocked. Please try again later.';
                } else {
                        errorMessage += error.message;
                    }
                    
                    // Hide loading overlay
                    loadingOverlay.style.display = 'none';
                    document.body.removeChild(loadingOverlay);
                    
                    showAlertModal('Upload Error', errorMessage, 'OK');
                } finally {
                    // Reset button state in parent function (if this was called directly)
                    if (typeof savePhotoBtn !== 'undefined' && savePhotoBtn) {
                        savePhotoBtn.disabled = false;
                        savePhotoBtn.innerHTML = '<i class="fas fa-check"></i> Save Photo';
                    }
                }
                return;
            }
            
            // Update the UI with the provided photo URL (could be from server or local)
            updateProfilePhotoUI(photoURL);
            return true; // Success
        } catch (error) {
            // Show error message
            handleNetworkError(error.message || 'Could not connect to server. Please check your internet connection and try again.');
            return false; // Failure
        }
    }
    
    // Helper function to update profile photo UI
    function updateProfilePhotoUI(photoURL) {
        const userAvatar = document.getElementById('userAvatar');
        const defaultAvatar = document.getElementById('defaultAvatar');
        
        // Create or get the image element
        let imgElement = userAvatar.querySelector('img');
        if (!imgElement) {
            imgElement = document.createElement('img');
            userAvatar.appendChild(imgElement);
        }
        
        // Update the image source
        imgElement.src = photoURL;
        
        // Hide the default avatar icon
        if (defaultAvatar) {
            defaultAvatar.style.display = 'none';
        }
        
        // Update user data
        userData.profilePhoto = photoURL;
    }

    // Initialize vehicle cards in the vehicle list
    function initVehicleList() {
        const vehicleList = document.getElementById('vehicleList');
        vehicleList.innerHTML = ''; // Clear existing cards
        
        // Check if we have any vehicles
        if (vehicles.length === 0) {
            // Also hide details and document section completely when no vehicles
            const detailsSection = document.getElementById('vehicleDetails');
            if (detailsSection) {
                detailsSection.style.display = 'none';
            }
            const documentUploadSection = document.querySelector('.document-upload-section');
            if (documentUploadSection) {
                documentUploadSection.style.display = 'none';
            }
            // Add CSS for empty state
            const style = document.createElement('style');
            style.textContent = `
                .empty-vehicles-state {
                    display: flex;
                    flex-direction: column;
                    align-items: center;
                    justify-content: center;
                    padding: 40px 20px;
                    text-align: center;
                    background-color: #fff;
                    border-radius: 12px;
                    margin: 15px 0;
                    
                }
                
                .empty-vehicles-state .empty-icon {
                    font-size: 60px;
                    color: #007BFF;
                    margin-bottom: 20px;
                    animation: bounce 2s infinite;
                }
                
                @keyframes bounce {
                    0%, 20%, 50%, 80%, 100% {
                        transform: translateY(0);
                    }
                    40% {
                        transform: translateY(-20px);
                    }
                    60% {
                        transform: translateY(-10px);
                    }
                }
                
                .empty-vehicles-state.error .empty-icon {
                    color: #dc3545;
                }
                
                .empty-vehicles-state h3 {
                    margin: 0 0 15px 0;
                    color: #333;
                    font-size: 28px;
                    font-weight: bold;
                }
                
                .empty-vehicles-state p {
                    color: #555;
                    margin-bottom: 20px;
                    font-size: 17px;
                }
                
                .loading-vehicles {
                    padding: 20px;
                    text-align: center;
                    color: #666;
                    font-size: 16px;
                }
                
                .loading-vehicles i {
                    margin-right: 10px;
                    color: #007BFF;
                }
                
                .creative-message {
                    background-color: #fff;
                    border-radius: 12px;
                    box-shadow: 0 4px 15px rgba(0,0,0,0.1);
                    padding: 25px;
                    margin: 20px 0 30px;
                    max-width: 650px;
                    width: 90%;
                }
                
                .creative-message p {
                    margin-bottom: 20px;
                    font-size: 18px;
                    line-height: 1.6;
                    color: #444;
                    text-align: left;
                    padding: 10px 15px;
                    border-left: 4px solid #ffa500;
                    background-color: #f8f9fa;
                    border-radius: 0 8px 8px 0;
                    transition: transform 0.3s;
                }
                
                .creative-message p:hover {
                    transform: translateX(5px);
                    background-color: #e9f5ff;
                }
                
                .creative-message p:last-child {
                    margin-bottom: 0;
                    font-weight: bold;
                    border-left-color: #28a745;
                    background-color: #f0f9f0;
                }
                
                #emptyStateAddBtn {
                    margin-top: 20px;
                    padding: 15px 30px;
                    font-size: 18px;
                    background: linear-gradient(360deg, #f7071ecf, #bdc3376e);
                    transition: transform 0.3s, box-shadow 0.3s;
                    border-radius: 16px;
                    box-shadow: 0 4px 15px rgba(0, 123, 255, 0.3);
                }
                
                #emptyStateAddBtn:hover {
                    transform: scale(1.05);
                    box-shadow: 0 6px 20px rgba(0, 123, 255, 0.4);
                }
                
                .pulse-btn {
                    animation: pulse 3s infinite;
                }
                
                @keyframes pulse {
                    0% {
                        box-shadow: 0 0 0 0 rgba(38, 215, 53, 0.7);
                    }
                    70% {
                        box-shadow: 0 0 0 15px rgba(0, 123, 255, 0);
                    }
                    100% {
                        box-shadow: 0 0 0 0 rgba(0, 123, 255, 0);
                    }
                }
                
                /* Document Badge Styles */
                .document-badge {
                    display: inline-block;
                    padding: 2px 6px;
                    border-radius: 4px;
                    font-size: 12px;
                    font-weight: bold;
                    color: white;
                    margin-left: 8px;
                    vertical-align: middle;
                    cursor: default;
                }
                
                .rc-badge {
                    background-color: #28a745;
                }
                
                .dl-badge {
                    background-color: #007bff;
                }
                
                /* Document Status Styles */
                .status-badge {
                    display: inline-block;
                    padding: 4px 8px;
                    border-radius: 4px;
                    font-size: 13px;
                    font-weight: bold;
                    color: white;
                }
                
                .status-badge.uploaded {
                    background-color: #ffc107;
                    color: #212529;
                }
                
                .status-badge.verified {
                    background-color: #28a745;
                }
                
                .status-badge.rejected {
                    background-color: #dc3545;
                }
                
                .status-badge.not_uploaded {
                    background-color: #6c757d;
                }
                
                /* Document Upload Button Styles */
                .upload-document-btn {
                    padding: 6px 12px;
                    border-radius: 4px;
                    font-size: 14px;
                    font-weight: bold;
                    color: white;
                    background-color: #007bff;
                    border: none;
                    cursor: pointer;
                    transition: background-color 0.2s;
                }
                
                .upload-document-btn:hover {
                    background-color: #0069d9;
                }
            `;
            document.head.appendChild(style);
            
            return; // Don't continue if no vehicles
        }
        
        vehicles.forEach((vehicle, index) => {
            const card = document.createElement('div');
            card.className = `vehicle-card ${index === 0 ? 'selected' : ''}`;
            card.setAttribute('data-vehicle', vehicle.id);
            
            // Add fallback status if not provided
            const status = vehicle.status || 'inactive';
            
            // Check if this is a manual cart
            const isManualCart = vehicle.type && vehicle.type.toLowerCase().includes('manual') || 
                                (vehicle.number && vehicle.number.startsWith('MANUAL-CART-'));
            
            // Create card content based on vehicle type
            if (isManualCart) {
                card.innerHTML = `
                    <div class="vehicle-number">Manual Cart</div>
                    <div class="vehicle-type">${escapeHtml(vehicle.type || '')}</div>
                `;
            } else {
            card.innerHTML = `
                <div class="vehicle-number">${escapeHtml(vehicle.number || '')}</div>
                <div class="vehicle-type">${escapeHtml(vehicle.type || '')}</div>
            `;
            }
            
            vehicleList.appendChild(card);
            
            // Add click event
            card.addEventListener('click', () => selectVehicle(vehicle));
        });
        
        // Show vehicle limit notice based on current vehicle count
        updateVehicleLimitNotice();
    }

    // Update vehicle limit notice based on membership
    function updateVehicleLimitNotice() {
        const vehicleLimitNotice = document.querySelector('.vehicle-limit-notice');
        if (!vehicleLimitNotice) return;
        
        // REMOVED: Don't check localStorage for membership status
        // Instead, use the userData.membership that was fetched from the database
        
        const currentVehicleCount = vehicles.length;
        const maxVehicles = userData.membership === 'Premium' ? 5 : 3;
        
        if (currentVehicleCount >= maxVehicles) {
            vehicleLimitNotice.innerHTML = `
                <div class="limit-notice-box">
                    <i class="fas fa-exclamation-triangle"></i>
                    <p>You've reached your vehicle limit (${currentVehicleCount}/${maxVehicles}).</p>
                    ${userData.membership !== 'Premium' ? 
                        `<button class="upgrade-btn" id="upgradeFromNoticeBtn">
                            <i class="fas fa-crown"></i> Upgrade to Premium
                        </button>` : ''
                    }
                    </div>
                `;
            
            // Add event listener to the upgrade button if present
            const upgradeBtn = document.getElementById('upgradeFromNoticeBtn');
            if (upgradeBtn) {
                upgradeBtn.addEventListener('click', showPremiumPlansModal);
            }
            } else {
            vehicleLimitNotice.innerHTML = `
                <div class="limit-info">
                    <p>Vehicle count: ${currentVehicleCount}/${maxVehicles}</p>
                    </div>
                `;
        }
    }

    // Select a vehicle and display its details
    function selectVehicle(vehicle) {
        if (!vehicle) {
            console.error("Cannot select vehicle: No vehicle data provided");
            return;
        }
        
        console.log("Selecting vehicle:", vehicle);
        
        // Set the global selected vehicle
        selectedVehicle = vehicle;
        
        // Store the selected vehicle ID in localStorage for persistence
        storeSelectedVehicleId(vehicle.id);
        
        // Update selected card in UI
        document.querySelectorAll('.vehicle-card').forEach(card => {
            card.classList.remove('selected');
            if (card.getAttribute('data-vehicle') === vehicle.id) {
                card.classList.add('selected');
            }
        });
        
        // Update vehicle details section
        const detailsSection = document.getElementById('vehicleDetails');
        if (!detailsSection) {
            console.error("Vehicle details section not found");
            return;
        }
        
        detailsSection.style.display = 'block';
        
        // Check if this is a manual cart
        const isManualCart = vehicle.type && vehicle.type.toLowerCase().includes('manual') || 
                             (vehicle.number && vehicle.number.startsWith('MANUAL-CART-'));
        
        // Show/hide document upload section based on vehicle type
        const documentUploadSection = document.querySelector('.document-upload-section');
        if (documentUploadSection) {
            documentUploadSection.style.display = isManualCart ? 'none' : 'block';
        }
        
        // Update vehicle number in header
        const vehicleNumberElement = document.getElementById('selectedVehicleNumber');
        if (vehicleNumberElement) {
            // Add document status badges if available
            let badgesHtml = '';
            
            // RC badge
            if (vehicle.rc) {
                badgesHtml += '<span class="document-badge rc-badge" title="RC Uploaded">RC</span>';
            }
            
            // DL badge
            if (vehicle.d_l) {
                badgesHtml += '<span class="document-badge dl-badge" title="Driving License Uploaded">DL</span>';
            }
            
            // For manual carts, don't show the vehicle number
            if (isManualCart) {
                vehicleNumberElement.innerHTML = `Manual Cart ${badgesHtml}`;
            } else {
            vehicleNumberElement.innerHTML = `${escapeHtml(vehicle.number || 'Unknown')} ${badgesHtml}`;
            }
        }
        
        // Initialize document status
        initializeDocumentStatus(vehicle);
        
        // Update details content
        const detailsContent = detailsSection.querySelector('.vehicle-details-content');
        if (!detailsContent) {
            console.error("Vehicle details content section not found");
            return;
        }
        
        // Handle missing data with defaults
        const vehicleOwner = vehicle.owner || userData.name || 'You';
        
        // Format registration date properly
        let registrationDate = 'Not available';
        if (vehicle.registrationDate) {
            try {
                // Parse the date (handles both yyyy-mm-dd format and other formats)
                const dateObj = new Date(vehicle.registrationDate);
                if (!isNaN(dateObj.getTime())) {
                    // Format date as DD MMM YYYY (e.g., 15 Jan 2024)
                    registrationDate = dateObj.toLocaleDateString('en-IN', {
                        day: 'numeric',
                        month: 'short',
                        year: 'numeric'
                    });
                }
            } catch (e) {
                console.error("Error formatting registration date:", e);
            }
        }
        
        const contactNumber = vehicle.contact || userData.contact || 'Not provided';
        const whatsappNumber = vehicle.whatsapp ? vehicle.whatsapp : 'WhatsApp number not provided';
        const alternateContactNumber = vehicle.alternateContact || 'Not provided';
        
        // Process location to prevent duplication
        let location = vehicle.location || 'Not specified';
        if (location !== 'Not specified') {
            const locationParts = location.split(',').map(part => part.trim());
            
            // Check for duplicate state entries
            if (locationParts.length > 2) {
                // Extract unique location parts to prevent duplications
                const uniqueParts = [];
                const seenParts = new Set();
                
                locationParts.forEach(part => {
                    const lowerPart = part.toLowerCase();
                    if (!seenParts.has(lowerPart)) {
                        seenParts.add(lowerPart);
                        uniqueParts.push(part);
                    }
                });
                
                // Rebuild the location string with unique parts
                location = uniqueParts.join(', ');
            }
        }
        
        const pincode = vehicle.pincode || 'Not specified';
        
        // Create HTML for vehicle details with conditional registration number
        let detailsHTML = `
            <div>
                <div class="detail-group">
                    <div class="detail-label">Vehicle Type</div>
                    <div class="detail-value">${escapeHtml(vehicle.type || 'Unknown')}</div>
                </div>`;
                
        // Only show vehicle number for non-manual carts
        if (!isManualCart) {
            detailsHTML += `
                <div class="detail-group">
                    <div class="detail-label">Vehicle Number</div>
                    <div class="detail-value">${escapeHtml(vehicle.number || 'Unknown')}</div>
                </div>`;
        }
        
        // Continue with the rest of the details
        detailsHTML += `
                <div class="detail-group">
                    <div class="detail-label">Owner Name</div>
                    <div class="detail-value">${escapeHtml(vehicleOwner)}</div>
                </div>
                <div class="detail-group">
                    <div class="detail-label">Registration Date</div>
                    <div class="detail-value">${escapeHtml(registrationDate)}</div>
                </div>
            </div>
            <div>
                <div class="detail-group">
                    <div class="detail-label">Contact Number</div>
                    <div class="detail-value">${escapeHtml(contactNumber)}</div>
                </div>
                <div class="detail-group">
                    <div class="detail-label">WhatsApp Number</div>
                    <div class="detail-value">${escapeHtml(whatsappNumber)}</div>
                </div>
                <div class="detail-group">
                    <div class="detail-label">Alternate Contact</div>
                    <div class="detail-value">${escapeHtml(alternateContactNumber)}</div>
                </div>
                <div class="detail-group">
                    <div class="detail-label">Location</div>
                    <div class="detail-value">${escapeHtml(location)}</div>
                </div>
                <div class="detail-group">
                    <div class="detail-label">Pincode</div>
                    <div class="detail-value">${escapeHtml(pincode)}</div>
                </div>
            </div>
        `;
        
        detailsContent.innerHTML = detailsHTML;
        
        // Add service highlights section to view mode
        const highlightsData = vehicle.highlights || {};
        const activeHighlights = [];
        
        // Collect all active highlights
        if (highlightsData.highlight1) activeHighlights.push(highlightsData.highlight1);
        if (highlightsData.highlight2) activeHighlights.push(highlightsData.highlight2);
        if (highlightsData.highlight3) activeHighlights.push(highlightsData.highlight3);
        if (highlightsData.highlight4) activeHighlights.push(highlightsData.highlight4);
        if (highlightsData.highlight5) activeHighlights.push(highlightsData.highlight5);
        
        // Update document status in the UI based on vehicle data
        if (vehicle.rc) {
            updateDocumentStatus('rc', 'uploaded', vehicle.rc);
        } else {
            updateDocumentStatus('rc', 'not_uploaded');
        }
        
        if (vehicle.d_l) {
            updateDocumentStatus('license', 'uploaded', vehicle.d_l);
        } else {
            updateDocumentStatus('license', 'not_uploaded');
        }
        
        // Create service highlights HTML
        let serviceHighlightsHTML = '';
        if (activeHighlights.length > 0) {
            serviceHighlightsHTML = `
                <div class="full-width service-highlights-view">
                    <div class="service-highlights-header">
                        <div class="service-highlights-title">Service Highlights</div>
                    </div>
                    <div class="service-highlights-list">
                        ${activeHighlights.map(highlight => `
                            <div class="highlight-tag">
                                <span>${escapeHtml(highlight)}</span>
                            </div>
                        `).join('')}
                    </div>
                </div>
            `;
        } else {
            serviceHighlightsHTML = `
                <div class="full-width service-highlights-view">
                    <div class="service-highlights-header">
                        <div class="service-highlights-title">Service Highlights</div>
                    </div>
                    <div class="service-highlights-empty">
                        No service highlights selected. Click "Edit Details" to add highlights.
                    </div>
                </div>
            `;
        }
        
        detailsContent.insertAdjacentHTML('beforeend', serviceHighlightsHTML);
        
        // Update photos grid
        const photosGrid = detailsSection.querySelector('.photos-grid');
        if (!photosGrid) {
            console.error("Photos grid not found");
            return;
        }
        
        // Use default images if photos are not available
        const defaultImage = "attached_assets/images/default-vehicle.png";
        
        // Get photos from vehicle data
        let photos = vehicle.photos || {};
        
        // Avoid logging PII / user-uploaded URLs to the console
        
        // For mock data, create default photo URLs
        if (USE_MOCK_DATA && (!photos || Object.keys(photos).length === 0)) {
            // Create mock photo URLs based on vehicle type
            const typeForImage = vehicle.type.toLowerCase().includes('tata ace') ? 
                'Tata Ace (Chhota Hathi)' : 
                (vehicle.type.toLowerCase().includes('eicher') ? 
                    'mini truck (Eicher Canter)' : 'bolero pickup-truck');
            
            photos = {
                front: `attached_assets/images/${typeForImage}.png`,
                side: `attached_assets/images/${typeForImage}.png`,
                back: `attached_assets/images/${typeForImage}.png`,
                loading: `attached_assets/images/${typeForImage}.png`
            };
        }
        
        // Fix any undefined photo values
        const photoTypes = ['front', 'side', 'back', 'loading'];
        photoTypes.forEach(type => {
            if (!photos[type]) {
                photos[type] = defaultImage;
            }
        });
        
        photosGrid.textContent = '';
        const photoDefs = [
            { key: 'front', label: 'Front View', alt: 'Front View' },
            { key: 'side', label: 'Side View', alt: 'Side View' },
            { key: 'back', label: 'Back View', alt: 'Back View' },
            { key: 'loading', label: 'Loading Area', alt: 'Loading Area' },
        ];
        photoDefs.forEach(({ key, label, alt }) => {
            const wrap = document.createElement('div');
            wrap.className = 'photo-item';

            const img = document.createElement('img');
            img.alt = alt;
            img.src = photos[key] || defaultImage;
            img.onerror = function () { this.src = defaultImage; };

            const lab = document.createElement('div');
            lab.className = 'photo-label';
            lab.textContent = label;

            wrap.appendChild(img);
            wrap.appendChild(lab);
            photosGrid.appendChild(wrap);
        });
    }
    
    // Service Highlights functionality
    let selectedVehicle = null; // Make it a global variable
    
    function initServiceHighlights() {
        // Keep the existing code, but no longer needed for main UI
    }
    
    // Initialize service highlights in edit mode
    function initEditHighlights(vehicle) {
        const checkboxes = document.querySelectorAll('#editServiceHighlightsGrid .highlight-checkbox');
        const countDisplay = document.getElementById('editHighlightsCount');
        const MAX_HIGHLIGHTS = 5;
        let selectedHighlightsCount = 0;
        
        // Reset all checkboxes
        checkboxes.forEach(cb => {
            cb.checked = false;
            cb.disabled = false;
            cb.parentElement.classList.remove('selected');
            cb.parentElement.classList.remove('disabled');
        });
        
        // Load existing highlights from vehicle data
        if (vehicle && vehicle.highlights) {
            const highlights = [
                vehicle.highlights.highlight1,
                vehicle.highlights.highlight2,
                vehicle.highlights.highlight3,
                vehicle.highlights.highlight4,
                vehicle.highlights.highlight5
            ].filter(h => h); // Filter out null/undefined
            
            // Count how many are selected
            selectedHighlightsCount = highlights.length;
            
            // Update checkboxes based on existing highlights
            checkboxes.forEach(cb => {
                const value = cb.value;
                if (highlights.includes(value)) {
                    cb.checked = true;
                    cb.parentElement.classList.add('selected');
                }
            });
        }
        
        // Update the count display
        if (countDisplay) {
            countDisplay.textContent = selectedHighlightsCount;
        }
        
        // Add event listeners to checkboxes
        checkboxes.forEach(checkbox => {
            checkbox.addEventListener('change', function() {
                // The parent container of the checkbox
                const container = this.parentElement;
                
                if (this.checked) {
                    // If trying to select more than MAX_HIGHLIGHTS, prevent it
                    if (selectedHighlightsCount >= MAX_HIGHLIGHTS) {
                        this.checked = false;
                        showToast(`You can select a maximum of ${MAX_HIGHLIGHTS} highlights`, 'warning');
                        return;
                    }
                    
                    // Add to the count and mark as selected
                    selectedHighlightsCount++;
                    container.classList.add('selected');
                } else {
                    // Reduce the count and remove the selected class
                    selectedHighlightsCount--;
                    container.classList.remove('selected');
                }
                
                // Update the count display
                if (countDisplay) {
                    countDisplay.textContent = selectedHighlightsCount;
                }
                
                // If at max, disable unselected checkboxes
                checkboxes.forEach(cb => {
                    if (!cb.checked) {
                        cb.disabled = selectedHighlightsCount >= MAX_HIGHLIGHTS;
                        cb.parentElement.classList.toggle('disabled', selectedHighlightsCount >= MAX_HIGHLIGHTS);
                    }
                });
            });
        });
    }
    
    // Update service highlights UI based on selected vehicle
    function loadVehicleHighlights(vehicle) {
        // This function is no longer needed as we now handle the highlights in the selectVehicle function directly
        // Keeping the function for backward compatibility
        console.log("loadVehicleHighlights is deprecated. Highlights are now shown in selectVehicle directly.");
    }

    // Show upgrade membership modal
    function showUpgradeModal() {
        // In a real application, this would show a modal with upgrade options
        showAlertModal(
            'Premium Membership Benefits',
            'Premium Membership includes:\n\n- Register up to 5 vehicles\n- Priority listing in search results\n- Exclusive booking opportunities\n- 24/7 customer support\n\nUpgrade for just ₹999/year',
            'Close'
        );
    }

    // Handle the Add Vehicle button
    function handleAddVehicle() {
        if (vehicles.length >= userData.maxVehicles) {
            if (userData.membership === 'Standard') {
                showConfirmModal(
                    'Vehicle Limit Reached',
                    'You have reached the vehicle limit for your Standard account. Would you like to upgrade to Premium to add more vehicles?',
                    'Upgrade to Premium',
                    'Not Now',
                    () => {
                    showUpgradeModal();
                }
                );
            } else {
                showAlertModal(
                    'Maximum Limit Reached',
                    'You have reached the maximum number of vehicles for your Premium account.',
                    'OK'
                );
            }
        } else {
            // In a real app, redirect to vehicle registration page
            window.location.href = 'register';
        }
    }

    // Handle the Edit Vehicle button
    function handleEditVehicle() {
        const selectedCard = document.querySelector('.vehicle-card.selected');
        if (!selectedCard) return;
        
        const vehicleId = selectedCard.getAttribute('data-vehicle');
        const vehicle = vehicles.find(v => v.id === vehicleId);
        
        if (vehicle) {
            // Get the details section and content
            const detailsSection = document.getElementById('vehicleDetails');
            const detailsContent = detailsSection.querySelector('.vehicle-details-content');
            
            // Get edit button and change its text and functionality to "Save Changes"
            const editBtn = document.getElementById('editVehicleBtn');
            const deleteBtn = document.getElementById('deleteVehicleBtn');
            
            // Store original button HTML to restore later
            const originalEditBtnHTML = editBtn.innerHTML;
            const originalDeleteBtnHTML = deleteBtn.innerHTML;
            
            // Change button to "Save Changes"
            editBtn.innerHTML = '<i class="fas fa-save"></i> Save Changes';
            editBtn.classList.add('save-btn');
            
            // Change delete button to "Cancel" button
            deleteBtn.innerHTML = '<i class="fas fa-times"></i> Cancel';
            deleteBtn.classList.add('cancel-btn');
            deleteBtn.classList.remove('delete-btn');
            deleteBtn.style.backgroundColor = '#6c757d'; // Gray color for cancel
            deleteBtn.style.opacity = '1';
            
            // Remove existing event listeners by cloning the button
            const cancelBtn = deleteBtn.cloneNode(true);
            deleteBtn.parentNode.replaceChild(cancelBtn, deleteBtn);
            
            // Add cancel functionality using addEventListener
            cancelBtn.addEventListener('click', function cancelEditMode(e) {
                e.preventDefault(); // Prevent default action
                e.stopPropagation(); // Stop event propagation
                exitEditMode();
            });
            
            // Replace static content with editable fields
            const vehicleOwner = vehicle.owner || userData.name || 'You';
            const contactNumber = vehicle.contact || userData.contact || 'Not provided';
            const whatsappNumber = vehicle.whatsapp || '';
            const alternateContactNumber = vehicle.alternateContact || '';
            
            // Process location to ensure it's clean for editing
            let locationValue = vehicle.location || 'Not specified';
            if (locationValue !== 'Not specified') {
                const locationParts = locationValue.split(',').map(part => part.trim());
                
                // Extract unique location parts
                const uniqueParts = [];
                const seenParts = new Set();
                
                locationParts.forEach(part => {
                    const lowerPart = part.toLowerCase();
                    if (!seenParts.has(lowerPart)) {
                        seenParts.add(lowerPart);
                        uniqueParts.push(part);
                    }
                });
                
                // Update location value with unique parts
                locationValue = uniqueParts.join(', ');
            }
            
            // Format registration date properly for display
            let registrationDateDisplay = 'Not available';
            if (vehicle.registrationDate) {
                try {
                    // Parse the date
                    const dateObj = new Date(vehicle.registrationDate);
                    if (!isNaN(dateObj.getTime())) {
                        // Format date as DD MMM YYYY (e.g., 15 Jan 2024)
                        registrationDateDisplay = dateObj.toLocaleDateString('en-IN', {
                            day: 'numeric',
                            month: 'short',
                            year: 'numeric'
                        });
                    }
                } catch (e) {
                    console.error("Error formatting registration date:", e);
                }
            }
            
            // Create editable content
            detailsContent.innerHTML = `
                <div>
                    <div class="detail-group">
                        <div class="detail-label">Vehicle Type</div>
                        <div class="detail-value non-editable">${vehicle.type || 'Unknown'}</div>
                        <small class="detail-hint">Vehicle type cannot be edited after registration.</small>
                    </div>
                    <div class="detail-group">
                        <div class="detail-label">Owner Name</div>
                        <input type="text" class="edit-input" id="editOwner" value="${vehicleOwner}">
                    </div>
                    <div class="detail-group">
                        <div class="detail-label">Registration Date</div>
                        <div class="detail-value non-editable">${registrationDateDisplay}</div>
                        <small class="detail-hint">Registration date cannot be modified.</small>
                    </div>
                </div>
                <div>
                    <div class="detail-group">
                        <div class="detail-label">Contact Number</div>
                        <input type="text" class="edit-input" id="editContact" value="${contactNumber}">
                    </div>
                    <div class="detail-group">
                        <div class="detail-label">WhatsApp Number</div>
                        <input type="text" class="edit-input" id="editWhatsapp" value="${whatsappNumber}">
                    </div>
                    <div class="detail-group">
                        <div class="detail-label">Alternate Contact</div>
                        <input type="text" class="edit-input" id="editAlternateContact" value="${alternateContactNumber}">
                        <small class="detail-hint">Additional contact number for customers</small>
                    </div>
                    <div class="detail-group">
                        <div class="detail-label">Location</div>
                        <input type="text" class="edit-input" id="editLocation" value="${locationValue}">
                    </div>
                    <div class="detail-group">
                        <div class="detail-label">Pincode</div>
                        <div class="detail-value non-editable">${vehicle.pincode || 'Not specified'}</div>
                        <small class="detail-hint">Pincode cannot be edited after registration.</small>
                    </div>
                </div>
            `;
            
            // Add service highlights section to edit form
            const serviceHighlightsHTML = `
                <div class="full-width">
                    <div class="service-highlights-header">
                        <div class="service-highlights-title">Service Highlights (Choose up to 5)</div>
                        <div class="highlights-count"><span id="editHighlightsCount">0</span>/5 selected</div>
                    </div>
                    <div class="service-highlights-grid" id="editServiceHighlightsGrid">
                        <!-- Checkboxes for service highlights -->
                        <div class="highlight-checkbox-container">
                            <input type="checkbox" id="editHighlight1" class="highlight-checkbox" value="Local City Only">
                            <label for="editHighlight1" class="highlight-label">Local City Only</label>
                        </div>
                        <div class="highlight-checkbox-container">
                            <input type="checkbox" id="editHighlight2" class="highlight-checkbox" value="Raat me seva nahi milegi">
                            <label for="editHighlight2" class="highlight-label">Raat me seva nahi milegi</label>
                        </div>
                        <div class="highlight-checkbox-container">
                            <input type="checkbox" id="editHighlight3" class="highlight-checkbox" value="24x7 Service">
                            <label for="editHighlight3" class="highlight-label">24x7 Service</label>
                        </div>
                        <div class="highlight-checkbox-container">
                            <input type="checkbox" id="editHighlight4" class="highlight-checkbox" value="Return Trip Allowed">
                            <label for="editHighlight4" class="highlight-label">Return Trip Allowed</label>
                        </div>
                        <div class="highlight-checkbox-container">
                            <input type="checkbox" id="editHighlight5" class="highlight-checkbox" value="One-way Trip Only">
                            <label for="editHighlight5" class="highlight-label">One-way Trip Only</label>
                        </div>
                        <div class="highlight-checkbox-container">
                            <input type="checkbox" id="editHighlight6" class="highlight-checkbox" value="All-India Permit">
                            <label for="editHighlight6" class="highlight-label">All-India Permit</label>
                        </div>
                        <div class="highlight-checkbox-container">
                            <input type="checkbox" id="editHighlight7" class="highlight-checkbox" value="Helper Available">
                            <label for="editHighlight7" class="highlight-label">Helper Available</label>
                        </div>
                        <div class="highlight-checkbox-container">
                            <input type="checkbox" id="editHighlight8" class="highlight-checkbox" value="Turant booking milegi">
                            <label for="editHighlight8" class="highlight-label">Turant booking milegi</label>
                        </div>
                        <div class="highlight-checkbox-container">
                            <input type="checkbox" id="editHighlight9" class="highlight-checkbox" value="Daily Services">
                            <label for="editHighlight9" class="highlight-label">Daily Services</label>
                        </div>
                        <div class="highlight-checkbox-container">
                            <input type="checkbox" id="editHighlight10" class="highlight-checkbox" value="Experienced Driver">
                            <label for="editHighlight10" class="highlight-label">Experienced Driver</label>
                        </div>
                        <div class="highlight-checkbox-container">
                            <input type="checkbox" id="editHighlight11" class="highlight-checkbox" value="Time pe delivery karenge">
                            <label for="editHighlight11" class="highlight-label">Time pe delivery karenge</label>
                        </div>
                    </div>
                </div>
            `;
            
            // Append the service highlights section to details content
            detailsContent.insertAdjacentHTML('beforeend', serviceHighlightsHTML);
            
            // Initialize service highlights in edit mode
            initEditHighlights(vehicle);
            
            // Add CSS style to change the appearance in edit mode
            const editStyle = document.createElement('style');
            editStyle.id = 'edit-mode-style';
            editStyle.innerHTML = `
                .detail-value { display: none; }
                .edit-input { 
                    display: block; 
                    width: 90%;
                    padding: 8px 12px;
                    border: 1px solid #ddd;
                    border-radius: 5px;
                    font-size: 16px;
                    margin-bottom: 5px;
                    transition: border-color 0.3s;
                }
                .edit-input:focus {
                    border-color: #007BFF;
                    outline: none;
                    box-shadow: 0 0 0 2px rgba(0, 123, 255, 0.25);
                }
                .non-editable { 
                    display: block !important;
                    color: #666;
                    font-style: italic;
                }
                small.detail-hint { 
                    display: block;
                    color: #888;
                    font-size: 12px;
                    margin-top: 2px;
                }
                .highlight-checkbox-container {
                    margin-bottom: 8px;
                    padding: 10px;
                    transition: all 0.2s ease;
                }
                .highlight-checkbox-container:hover {
                    background-color: #f0f7ff;
                }
                .highlight-checkbox-container.selected {
                    background-color: #e6f7ff;
                    border-color: #1890ff;
                    box-shadow: 0 0 0 1px #1890ff;
                }
                .highlight-checkbox {
                    margin-right: 10px;
                    cursor: pointer;
                }
                .highlight-label {
                    cursor: pointer;
                }
                .save-btn {
                    background-color: #28a745;
                }
                .save-btn:hover {
                    background-color: #218838;
                }
                .cancel-btn {
                    background-color: #6c757d;
                }
                .cancel-btn:hover {
                    background-color: #5a6268;
                }
                #editServiceHighlightsGrid {
                    margin-top: 15px;
                    background-color: #f9f9f9;
                    padding: 15px;
                    border-radius: 8px;
                    border: 1px solid #eee;
                }
                .invalid-input {
                    border-color: #dc3545 !important;
                    background-color: #fff8f8;
                }
                .invalid-input:focus {
                    box-shadow: 0 0 0 2px rgba(220, 53, 69, 0.25) !important;
                }
                .validation-message {
                    color: #dc3545;
                    font-size: 12px;
                    margin-top: 4px;
                    display: none;
                }
                .validation-message.show {
                    display: block;
                }
            `;
            document.head.appendChild(editStyle);
            
            // Set up phone number validation for contact fields
            setupPhoneValidation();
            
            // Define the save changes handler
            const saveChangesHandler = function() {
                // Validate phone numbers before saving
                const contactInput = document.getElementById('editContact');
                const whatsappInput = document.getElementById('editWhatsapp');
                const alternateContactInput = document.getElementById('editAlternateContact');
                
                // Validation function for phone numbers
                const validatePhoneNumber = (input) => {
                    if (!input.value) return true; // Empty is allowed
                    
                    // Check if it's a valid 10-digit number
                    const isValid = /^\d{10}$/.test(input.value);
                    
                    if (!isValid) {
                        input.classList.add('invalid-input');
                        return false;
                    }
                    
                    input.classList.remove('invalid-input');
                    return true;
                };
                
                // Validate all phone fields
                const isContactValid = validatePhoneNumber(contactInput);
                const isWhatsappValid = validatePhoneNumber(whatsappInput);
                const isAlternateValid = validatePhoneNumber(alternateContactInput);
                
                // If contact number validation fails, show error and stop
                // WhatsApp and alternate contact can be empty
                if (!isContactValid) {
                    showToast('Please enter a valid 10-digit contact number', 'error');
                    return;
                }
                
                // For WhatsApp and alternate, only validate if not empty
                if (whatsappInput.value && !isWhatsappValid) {
                    showToast('Please enter a valid 10-digit WhatsApp number', 'error');
                    return;
                }
                
                if (alternateContactInput.value && !isAlternateValid) {
                    showToast('Please enter a valid 10-digit alternate contact number', 'error');
                    return;
                }
                
                // Create a processing overlay to prevent multiple clicks
                const processingOverlay = document.createElement('div');
                processingOverlay.className = 'processing-overlay';
                processingOverlay.innerHTML = '<div class="spinner"></div><div class="message">Saving changes...</div>';
                detailsSection.appendChild(processingOverlay);
                
                // Get vehicle ID
                const vehicleId = vehicle.id;
                if (!vehicleId) {
                    console.error("Cannot save changes: No vehicle ID");
                    detailsSection.removeChild(processingOverlay);
                    exitEditMode(); // Exit edit mode on error
                    return;
                }
                
                // Get selected highlights
                const selectedCheckboxes = Array.from(document.querySelectorAll('#editServiceHighlightsGrid .highlight-checkbox:checked'));
                const highlightsData = {
                    highlight1: selectedCheckboxes.length > 0 ? selectedCheckboxes[0].value : null,
                    highlight2: selectedCheckboxes.length > 1 ? selectedCheckboxes[1].value : null,
                    highlight3: selectedCheckboxes.length > 2 ? selectedCheckboxes[2].value : null,
                    highlight4: selectedCheckboxes.length > 3 ? selectedCheckboxes[3].value : null,
                    highlight5: selectedCheckboxes.length > 4 ? selectedCheckboxes[4].value : null
                };
                
                // Get updated values
                const updatedVehicle = {
                    id: vehicle.id,
                    type: vehicle.type, // Keep original value
                    number: vehicle.number, // Keep original value
                    owner: document.getElementById('editOwner').value,
                    registrationDate: vehicle.registrationDate, // Keep original value
                    contact: document.getElementById('editContact').value,
                    whatsapp: document.getElementById('editWhatsapp').value,
                    location: document.getElementById('editLocation').value.trim(), // Trim any extra spaces
                    pincode: vehicle.pincode, // Keep original value
                    status: vehicle.status, // Preserve current status
                    alternateContact: document.getElementById('editAlternateContact').value,
                    photos: vehicle.photos // Keep original photos
                };
                
                // Add highlights data to the updated vehicle
                updatedVehicle.highlights = highlightsData;
                
                // Process location to ensure it has the state and no duplicates
                if (updatedVehicle.location) {
                    // First, ensure state is included
                    const locationParts = updatedVehicle.location.split(',').map(part => part.trim());
                    const originalLocationParts = vehicle.location ? vehicle.location.split(',').map(part => part.trim()) : [];
                    
                    // Get the state from original location (usually the last part)
                    const state = originalLocationParts.length >= 2 ? originalLocationParts[originalLocationParts.length - 1] : null;
                    
                    // Extract unique location parts
                    const uniqueParts = [];
                    const seenParts = new Set();
                    
                    locationParts.forEach(part => {
                        const lowerPart = part.toLowerCase();
                        if (!seenParts.has(lowerPart)) {
                            seenParts.add(lowerPart);
                            uniqueParts.push(part);
                        }
                    });
                    
                    // Ensure state is included
                    if (state && !seenParts.has(state.toLowerCase())) {
                        uniqueParts.push(state);
                    }
                    
                    // Rebuild the location string with unique parts
                    updatedVehicle.location = uniqueParts.join(', ');
                }
                
                // Store the vehicle ID for persistence
                storeSelectedVehicleId(vehicleId);
                
                // Send the update to the server
                fetch(getApiUrl(`vehicles/${vehicleId}`), {
                    method: 'PUT',
                    headers: {
                        'Content-Type': 'application/json'
                    },
                    body: JSON.stringify({
                    owner: updatedVehicle.owner,
                    contact: updatedVehicle.contact,
                    whatsapp: updatedVehicle.whatsapp,
                    whatsappNumber: updatedVehicle.whatsapp, // Include both field names for compatibility
                    whatsappNo: updatedVehicle.whatsapp, // Add one more field name for extra compatibility
                    alternateContact: updatedVehicle.alternateContact,
                    location: updatedVehicle.location, // Send the processed location
                    // Add highlights data to the request
                    highlight1: updatedVehicle.highlights.highlight1,
                    highlight2: updatedVehicle.highlights.highlight2,
                    highlight3: updatedVehicle.highlights.highlight3,
                    highlight4: updatedVehicle.highlights.highlight4,
                    highlight5: updatedVehicle.highlights.highlight5
                    })
                })
                .then(response => {
                    if (!response.ok) {
                        throw new Error(`Server responded with status: ${response.status}`);
                    }
                    return response.json();
                })
                .then(data => {
                    console.log('Vehicle update response:', data);
                
                // Also make a direct API call to update the WhatsApp number specifically
                    // This ensures the WhatsApp number is updated correctly
                    if (updatedVehicle.whatsapp) {
                        console.log('Sending WhatsApp update for vehicle:', vehicleId);
                fetch(getApiUrl(`vehicles/${vehicleId}/whatsapp`), {
                    method: 'POST',
                    headers: {
                        'Content-Type': 'application/json'
                    },
                    body: JSON.stringify({
                        whatsapp: updatedVehicle.whatsapp,
                        whatsappNumber: updatedVehicle.whatsapp,
                        whatsappNo: updatedVehicle.whatsapp
                    })
                })
                .then(response => {
                    if (!response.ok) {
                        console.warn(`WhatsApp specific update returned status: ${response.status}`);
                    } else {
                        console.log('WhatsApp specific update successful');
                    }
                })
                .catch(error => {
                    console.error('Error in WhatsApp specific update:', error);
                });
                    }
                    
                    // Remove processing overlay
                    detailsSection.removeChild(processingOverlay);
                    
                    if (data.success) {
                        // Update the vehicle in our array
                        const index = vehicles.findIndex(v => v.id === vehicleId);
                        if (index > -1) {
                            // Update the vehicle object
                            Object.assign(vehicles[index], updatedVehicle);
                            
                            // Clean up the location field
                            if (vehicles[index].location) {
                                const locationParts = vehicles[index].location.split(',').map(part => part.trim());
                                const seenParts = new Set();
                                const uniqueParts = [];
                                
                                locationParts.forEach(part => {
                                    const lowerPart = part.toLowerCase();
                                    if (!seenParts.has(lowerPart)) {
                                        seenParts.add(lowerPart);
                                        uniqueParts.push(part);
                                    }
                                });
                                
                                // Rebuild the location string with unique parts
                                vehicles[index].location = uniqueParts.join(', ');
                                console.log('Updated and cleaned location from server:', vehicles[index].location);
                            }
                        }
                        
                        // Update UI
                        updateVehicleCard(selectedCard, updatedVehicle);
                        
                        // Return to view mode
                        exitEditMode();
                        
                        // Reload the vehicle data from server to ensure we have the latest data
                        reloadVehicleData(vehicle.id, () => {
                            // Refresh the vehicle details view with complete vehicle data
                            const freshVehicle = vehicles.find(v => v.id === vehicle.id);
                            if (freshVehicle) {
                            selectVehicle(freshVehicle);
                            }
                            
                            // Show success message
                            showToast('Vehicle updated successfully');
                            
                            // Refresh the page after successful update
                            setTimeout(() => {
                                try {
                                    // Use a full page reload with timestamp to prevent caching issues
                                    const timestamp = new Date().getTime();
                                    const baseUrl = window.location.href.split('#')[0];
                                    const newUrl = baseUrl + (baseUrl.includes('?') ? '&' : '?') + '_t=' + timestamp;
                                    console.log('Reloading page with cache-busting URL:', newUrl);
                                    window.location.href = newUrl;
                                } catch (error) {
                                    console.error('Error during page reload:', error);
                                    // Fallback to simple reload if the above fails
                                    window.location.reload(true);
                                }
                            }, 1500); // 1.5 seconds delay so user can see success message
                        });
                    } else {
                        // Return to view mode
                        exitEditMode();
                        
                        // Show error message
                        showToast(data.message || 'Failed to update vehicle', 'error');
                    }
                })
                .catch(error => {
                    // Remove processing overlay
                    detailsSection.removeChild(processingOverlay);
                    
                    // Return to view mode
                    exitEditMode();
                    
                    console.error('Error updating vehicle:', error);
                    showToast('Failed to update vehicle. Please try again later.', 'error');
                });
            };
            
            // Function to exit edit mode
            function exitEditMode() {
                // Remove edit style
                const editStyle = document.getElementById('edit-mode-style');
                if (editStyle) {
                    document.head.removeChild(editStyle);
                }
                
                // Restore original button
                editBtn.innerHTML = originalEditBtnHTML;
                editBtn.classList.remove('save-btn');
                
                // Get reference to the current cancel button that replaced the delete button
                const currentCancelBtn = document.getElementById('deleteVehicleBtn');
                
                // Restore delete button - create new clean button to remove all event listeners
                const brandNewDeleteBtn = document.createElement('button');
                brandNewDeleteBtn.id = 'deleteVehicleBtn';
                brandNewDeleteBtn.className = 'vehicle-action-btn delete-btn';
                brandNewDeleteBtn.innerHTML = originalDeleteBtnHTML;
                
                // Replace the current button with the new one
                currentCancelBtn.parentNode.replaceChild(brandNewDeleteBtn, currentCancelBtn);
                
                // Add the original delete functionality back
                brandNewDeleteBtn.addEventListener('click', handleDeleteVehicle);
                
                // Very important: Remove all previous event listeners and add a new one
                // This is needed because the old event listeners are still attached
                editBtn.replaceWith(editBtn.cloneNode(true)); 
                
                // Get the new button after replacing
                const newEditBtn = document.getElementById('editVehicleBtn');
                newEditBtn.addEventListener('click', handleEditVehicle);
                
                // Refresh vehicle view
                const freshVehicle = vehicles.find(v => v.id === vehicle.id);
                if (freshVehicle) {
                    selectVehicle(freshVehicle);
                }
            }
            
            // Remove the old click event and add the new one
            editBtn.removeEventListener('click', handleEditVehicle);
            editBtn.addEventListener('click', saveChangesHandler);
        }
    }

    // Helper function to update a vehicle card with new data
    function updateVehicleCard(card, vehicle) {
        // Update the card's vehicle number and type
        const numberElement = card.querySelector('.vehicle-number');
        if (numberElement) {
            numberElement.textContent = vehicle.number;
        }
        
        const typeElement = card.querySelector('.vehicle-type');
        if (typeElement) {
            typeElement.textContent = vehicle.type;
        }
    }

    // Function to store the current selected vehicle ID for persistence across page loads
    function storeSelectedVehicleId(vehicleId) {
        if (vehicleId) {
            localStorage.setItem('lastSelectedVehicleId', vehicleId);
            console.log('Stored vehicle ID in localStorage:', vehicleId);
            
            // Also find and store the full vehicle data if available
            const vehicle = vehicles.find(v => v.id === vehicleId);
            if (vehicle) {
                try {
                    const vehicleKey = `vehicle_${vehicleId}`;
                    localStorage.setItem(vehicleKey, JSON.stringify({
                        ...vehicle,
                        lastUpdated: new Date().getTime()
                    }));
                    console.log(`Stored full vehicle data for ID ${vehicleId} in localStorage:`, vehicle);
                } catch (e) {
                    console.error(`Error storing vehicle data for ID ${vehicleId} in localStorage:`, e);
                }
            }
        }
    }

    // Function to reload a specific vehicle's data from the server
    function reloadVehicleData(vehicleId, callback) {
        // Store the current vehicle ID for persistence
        storeSelectedVehicleId(vehicleId);
        
        if (USE_MOCK_DATA) {
            // For mock data, just call the callback immediately
            if (callback && typeof callback === 'function') {
                setTimeout(callback, 100);
            }
            return;
        }
        
        // For real implementation, fetch the vehicle data from the server
        const userPhone = localStorage.getItem('userPhone');
        if (!userPhone) {
            console.error("User phone not found in local storage");
            if (callback && typeof callback === 'function') {
                callback();
            }
            return;
        }
        
        // Show a loading indicator (optional)
        const loadingToast = showToast('Refreshing vehicle data...', 'info', false);
        
        // Fetch the specific vehicle data with proper error handling
        fetch(getApiUrl(`vehicles/${vehicleId}`))
        .then(response => {
            if (!response.ok) {
                // Try the registration endpoint as fallback
                return fetch(getApiUrl(`registration/${vehicleId}`))
                    .then(fallbackResponse => {
                        if (!fallbackResponse.ok) {
                            throw new Error(`Failed to fetch vehicle data: ${response.status}, fallback: ${fallbackResponse.status}`);
                        }
                        return fallbackResponse.json();
                    });
            }
            return response.json();
        })
        .then(data => {
                console.log('Received vehicle data from server:', data);
                
                // Fetch images for this registration
                const imagesPromise = fetch(getApiUrl(`vehicles/${vehicleId}/images`))
                    .then(imagesResponse => {
                        if (!imagesResponse.ok) {
                            // Try fallback endpoint
                            return fetch(getApiUrl(`registration-images/${vehicleId}`))
                                .then(fallbackResponse => {
                                    if (!fallbackResponse.ok) {
                                        console.warn(`Failed to fetch vehicle images: ${imagesResponse.status}, fallback: ${fallbackResponse.status}`);
                            return { images: [], folderPath: null };
                                    }
                                    return fallbackResponse.json().then(imagesData => {
                                        return {
                                            images: imagesData.imageUrls || [],
                                            folderPath: imagesData.folderPath
                                        };
                                    });
                                })
                                .catch(error => {
                                    console.warn('Error in fallback image fetch:', error);
                                    return { images: [], folderPath: null };
                                });
                        }
                        return imagesResponse.json().then(imagesData => {
                            return {
                                images: imagesData.imageUrls || [],
                                folderPath: imagesData.folderPath
                            };
                        });
                    })
                    .catch(imageError => {
                        console.warn('Error fetching images:', imageError);
                        return { images: [], folderPath: null };
                    });
                
                // Fetch document status for this registration
                const documentsPromise = fetch(getApiUrl(`vehicles/${vehicleId}/documents`))
                    .then(documentsResponse => {
                        if (!documentsResponse.ok) {
                            // Try fallback endpoint
                            return fetch(getApiUrl(`registration/${vehicleId}/documents`))
                                .then(fallbackResponse => {
                                    if (!fallbackResponse.ok) {
                                        console.warn(`Failed to fetch document status: ${documentsResponse.status}, fallback: ${fallbackResponse.status}`);
                            return { documents: {} };
                                    }
                                    return fallbackResponse.json();
                                })
                                .catch(error => {
                                    console.warn('Error in fallback document fetch:', error);
                                    return { documents: {} };
                                });
                        }
                        return documentsResponse.json();
                    })
                    .catch(documentsError => {
                        console.warn('Error fetching document status:', documentsError);
                        return { documents: {} };
                    });
                
                // Wait for both requests to complete
                return Promise.all([imagesPromise, documentsPromise])
                    .then(([imagesData, documentsData]) => {
                        return {
                            vehicle: data,
                            images: imagesData.images,
                            folderPath: imagesData.folderPath,
                            documents: documentsData.documents || {}
                        };
                    });
            })
            .then(completeData => {
            // Remove loading toast
            if (loadingToast && loadingToast.remove) {
                loadingToast.remove();
            }
            
                // Handle different response formats
                const vehicleData = completeData.vehicle.registration || completeData.vehicle;
                
                if (vehicleData) {
                // Update the vehicle in our array
                const index = vehicles.findIndex(v => v.id === vehicleId);
                if (index > -1) {
                        console.log('Reloaded vehicle data from server:', vehicleData);
                        
                        // Convert to our vehicle format if needed
                        const updatedVehicle = {
                            id: vehicleId,
                            number: vehicleData.vehiclePlateNumber || vehicleData.vehicleNumber || vehicleData.number,
                            type: vehicleData.vehicleType || vehicleData.type,
                            status: 'active',
                            owner: vehicleData.fullName || vehicleData.owner,
                            registrationDate: vehicleData.registrationDate,
                            contact: vehicleData.contactNumber || vehicleData.contact,
                            whatsapp: vehicleData.whatsappNumber || vehicleData.whatsapp,
                            whatsappNumber: vehicleData.whatsappNumber || vehicleData.whatsapp, // Also set whatsappNumber for consistency
                            alternateContact: vehicleData.alternateContactNumber || vehicleData.alternateContact,
                            location: `${vehicleData.city || ''}, ${vehicleData.state || ''}`,
                            pincode: vehicleData.pincode,
                            // Add document URLs
                            rc: vehicleData.rc || null,
                            d_l: vehicleData.d_l || null
                        };
                        
                        // Update document status UI based on vehicle data
                        if (updatedVehicle.rc) {
                            console.log("Found RC URL in vehicle data:", updatedVehicle.rc);
                            updateDocumentStatus('rc', 'uploaded', updatedVehicle.rc);
                        } else {
                            updateDocumentStatus('rc', 'not_uploaded');
                        }
                        
                        if (updatedVehicle.d_l) {
                            console.log("Found DL URL in vehicle data:", updatedVehicle.d_l);
                            updateDocumentStatus('license', 'uploaded', updatedVehicle.d_l);
                        } else {
                            updateDocumentStatus('license', 'not_uploaded');
                        }
                        
                        // If we have document information from the documents endpoint, use it (priority over vehicle data)
                        if (completeData.documents) {
                            if (completeData.documents.rc && completeData.documents.rc.url) {
                                console.log("Found RC URL in documents endpoint:", completeData.documents.rc.url);
                                updatedVehicle.rc = completeData.documents.rc.url;
                                updateDocumentStatus('rc', 'uploaded', completeData.documents.rc.url);
                            }
                            
                            if (completeData.documents.dl && completeData.documents.dl.url) {
                                console.log("Found DL URL in documents endpoint:", completeData.documents.dl.url);
                                updatedVehicle.d_l = completeData.documents.dl.url;
                                updateDocumentStatus('license', 'uploaded', completeData.documents.dl.url);
                            }
                        }
                        
                        // Add images from the separate API call
                        if (completeData.images && completeData.images.length > 0) {
                            // Filter out any hidden folder marker files and invalid URLs
                            const validImages = completeData.images.filter(url => 
                                url && typeof url === 'string' && 
                                !url.endsWith('.hidden_folder') && 
                                !url.endsWith('.folder') && 
                                url.trim() !== '');
                                
                            console.log(`Found ${validImages.length} valid images after filtering`);
                            updatedVehicle.images = validImages;
                        }
                        
                        if (completeData.folderPath) {
                            updatedVehicle.imageFolderPath = completeData.folderPath;
                        }
                    
                    // Process location to prevent duplication of state
                        if (updatedVehicle.location) {
                            const locationParts = updatedVehicle.location.split(',').map(part => part.trim()).filter(Boolean);
                        
                        // Clean up location data to ensure no duplicates
                        const uniqueParts = [];
                        const seenParts = new Set();
                        
                        locationParts.forEach(part => {
                                if (part) {  // Skip empty parts
                            const lowerPart = part.toLowerCase();
                            if (!seenParts.has(lowerPart)) {
                                seenParts.add(lowerPart);
                                uniqueParts.push(part);
                                    }
                            }
                        });
                        
                        // Rebuild the location string with unique parts
                            updatedVehicle.location = uniqueParts.join(', ');
                        }
                        
                        // Add or update service highlights
                        updatedVehicle.highlights = {
                            highlight1: vehicleData.highlight1 || null,
                            highlight2: vehicleData.highlight2 || null,
                            highlight3: vehicleData.highlight3 || null,
                            highlight4: vehicleData.highlight4 || null,
                            highlight5: vehicleData.highlight5 || null
                        };
                        
                        updatedVehicle.serviceHighlights = [];
                        if (vehicleData.highlight1) updatedVehicle.serviceHighlights.push(vehicleData.highlight1);
                        if (vehicleData.highlight2) updatedVehicle.serviceHighlights.push(vehicleData.highlight2);
                        if (vehicleData.highlight3) updatedVehicle.serviceHighlights.push(vehicleData.highlight3);
                        if (vehicleData.highlight4) updatedVehicle.serviceHighlights.push(vehicleData.highlight4);
                        if (vehicleData.highlight5) updatedVehicle.serviceHighlights.push(vehicleData.highlight5);
                        
                        vehicles[index] = updatedVehicle;
                        console.log("Vehicle data reloaded successfully:", updatedVehicle);
                }
            }
            
            // Call the callback function
            if (callback && typeof callback === 'function') {
                callback();
            }
        })
        .catch(error => {
            // Remove loading toast
            if (loadingToast && loadingToast.remove) {
                loadingToast.remove();
            }
            
            console.error("Error reloading vehicle data:", error);
                
                // Show error message to user
                showToast("Failed to refresh vehicle data. Please try again later.", "error");
            
            // Still call the callback to ensure UI updates
            if (callback && typeof callback === 'function') {
                callback();
            }
        });
    }

    // Show a custom edit modal for the vehicle
    function showEditModal(title, content, confirmText, cancelText, onConfirm, onCancel) {
        // Check if modal already exists, remove it if it does
        const existingModal = document.getElementById('customEditModal');
        if (existingModal) {
            document.body.removeChild(existingModal);
        }
        
        // Create modal element
        const modal = document.createElement('div');
        modal.id = 'customEditModal';
        modal.className = 'custom-modal';
        
        // Create modal content
        modal.innerHTML = `
            <div class="custom-modal-content edit-modal-content">
                <div class="custom-modal-header">
                    <h3>${title}</h3>
                    <button class="custom-modal-close">&times;</button>
                </div>
                <div class="custom-modal-body">
                    ${content}
                </div>
                <div class="custom-modal-footer">
                    <button class="custom-modal-btn custom-modal-cancel">${cancelText || 'Cancel'}</button>
                    <button class="custom-modal-btn custom-modal-confirm">${confirmText || 'Confirm'}</button>
                </div>
            </div>
        `;
        
        // Add styles for the edit form
        const style = document.createElement('style');
        style.textContent = `
            .edit-modal-content {
                max-width: 600px;
            }
            
            .form-group {
                margin-bottom: 15px;
            }
            
            .form-group label {
                display: block;
                margin-bottom: 5px;
                font-weight: bold;
                color: #555;
            }
            
            .form-control {
                width: 100%;
                padding: 8px 12px;
                border: 1px solid #ddd;
                border-radius: 4px;
                font-size: 16px;
            }
            
            .form-control:focus {
                border-color: #007BFF;
                outline: none;
                box-shadow: 0 0 0 3px rgba(0, 123, 255, 0.25);
            }

            .form-control[readonly] {
                background-color: #f9f9f9;
                color: #6c757d;
                cursor: not-allowed;
                border: 1px solid #ddd;
            }
            
            .form-text {
                display: block;
                margin-top: 5px;
                font-size: 12px;
            }
            
            .text-muted {
                color: #6c757d;
            }
            
            .custom-modal-confirm {
                background-color: #007BFF;
                color: white;
            }
            
            .custom-modal-confirm:hover {
                background-color: #0069d9;
            }
        `;
        document.head.appendChild(style);
        
        // Add modal to body
        document.body.appendChild(modal);
        
        // Add event listeners
        const closeBtn = modal.querySelector('.custom-modal-close');
        const cancelBtn = modal.querySelector('.custom-modal-cancel');
        const confirmBtn = modal.querySelector('.custom-modal-confirm');
        
        const closeModal = () => {
            modal.classList.remove('active');
            setTimeout(() => {
                modal.remove();
                document.head.removeChild(style);
            }, 300);
        };
        
        closeBtn.addEventListener('click', () => {
            closeModal();
            if (onCancel) onCancel();
        });
        
        cancelBtn.addEventListener('click', () => {
            closeModal();
            if (onCancel) onCancel();
        });
        
        confirmBtn.addEventListener('click', () => {
            closeModal();
            if (onConfirm) onConfirm();
        });
        
        // Show modal with animation
        setTimeout(() => {
            modal.classList.add('active');
        }, 10);
        
        return modal;
    }

    // Function to verify if a vehicle was actually deleted
    function verifyVehicleDeletion(vehicleId, maxRetries = 1) {
        let retryCount = 0;
        
        function checkAndRetry() {
            console.log(`Verifying deletion of vehicle ID: ${vehicleId} (attempt ${retryCount + 1})`);
            
            // Check if the vehicle still exists
            fetch(getApiUrl(`vehicles/${vehicleId}`))
                .then(async response => {
                    if (maybeForceLogoutOnAuthError(response)) {
                        throw new Error('Auth invalid; redirecting');
                    }

                    // Backend should return 404 when not found. Some older versions returned 400.
                    if (response.status === 404) {
                        console.log(`Verified: Vehicle ${vehicleId} was successfully deleted`);
                        return { deleted: true };
                    }

                    if (response.status === 400) {
                        try {
                            const data = await response.json();
                            const msg = String(data?.message || '').toLowerCase();
                            if (msg.includes('not found')) {
                                console.log(`Verified: Vehicle ${vehicleId} was successfully deleted`);
                                return { deleted: true };
                            }
                        } catch (_e) {
                            // ignore
                        }
                    }

                    if (response.ok) {
                        // Vehicle still exists - deletion failed
                        console.warn(`Vehicle ${vehicleId} still exists after deletion attempt`);
                        return { deleted: false };
                    }

                    // Unexpected non-OK
                    console.warn(`Unexpected response while verifying deletion (status=${response.status})`);
                    return { deleted: null };
                })
                .then(result => {
                    if (!result || result.deleted !== false) return;

                    // Retry if we haven't reached max retries
                    if (retryCount < maxRetries) {
                        retryCount++;
                        console.log(`Retrying deletion for vehicle ${vehicleId} (attempt ${retryCount + 1})`);

                        // Retry the deletion with the force-delete endpoint
                        fetch(getApiUrl(`vehicles/force-delete/${vehicleId}`), {
                            method: 'DELETE',
                            headers: {
                                'Content-Type': 'application/json'
                            }
                        })
                        .then(deleteResponse => {
                            console.log(`Retry deletion response: ${deleteResponse.status}`);
                            setTimeout(checkAndRetry, 1000);
                        })
                        .catch(error => {
                            console.error(`Error during retry deletion: ${error}`);
                        });
                    } else {
                        console.error(`Failed to delete vehicle ${vehicleId} after ${maxRetries + 1} attempts`);
                        showToast('Vehicle may not have been fully deleted. Please refresh the page.', 'warning');
                    }
                })
                .catch(error => {
                    if (isForcedLogoutInProgress) return;
                    console.error(`Error verifying vehicle deletion: ${error}`);
                });
        }
        
        // Start the verification process after a short delay
        setTimeout(checkAndRetry, 1000);
    }

    // Handle the Delete Vehicle button
    function handleDeleteVehicle() {
        const selectedCard = document.querySelector('.vehicle-card.selected');
        if (!selectedCard) return;
        
        const vehicleId = selectedCard.getAttribute('data-vehicle');
        const vehicle = vehicles.find(v => String(v.id) === String(vehicleId));
        
        if (vehicle) {
            showConfirmModal(
                'Delete Vehicle',
                `Are you sure you want to delete vehicle ${vehicle.number}?`,
                'Delete',
                'Cancel',
                () => {
                    // Show loading state
                    const loadingToast = showToast('Deleting vehicle...', 'info', false);
                    
                    console.log(`Attempting to delete vehicle with ID: ${vehicleId}`);

                    // Use fetch so env.js can auto-attach Authorization: Bearer <authToken>
                    fetch(getApiUrl(`vehicles/delete/${vehicleId}`), {
                        method: 'DELETE',
                        headers: {
                            'Content-Type': 'application/json'
                        }
                    })
                    .then(async response => {
                        console.log(`Delete vehicle response status: ${response.status}`);
                        if (maybeForceLogoutOnAuthError(response)) {
                            throw new Error('Auth invalid; redirecting');
                        }

                        let data = {};
                        try {
                            data = await response.json();
                        } catch (_e) {
                            data = {};
                        }

                        if (!response.ok) {
                            const msg = data?.message || `Failed to delete vehicle (HTTP ${response.status})`;
                            throw new Error(msg);
                        }
                        return data;
                    })
                    .then(data => {
                        console.log('Delete vehicle response data:', data);

                        if (!data || !data.success) {
                            console.error('Server returned success:false for vehicle deletion:', data);
                            showToast(data?.message || 'Failed to delete vehicle', 'error');
                            return;
                        }

                        // Remove from our array
                        const index = vehicles.findIndex(v => String(v.id) === String(vehicleId));
                        if (index > -1) {
                            vehicles.splice(index, 1);
                        }

                        // Update UI
                        selectedCard.remove();

                        // Select another vehicle if available
                        const remainingCards = document.querySelectorAll('.vehicle-card');
                        if (remainingCards.length > 0) {
                            const firstVehicleId = remainingCards[0].getAttribute('data-vehicle');
                            const firstVehicle = vehicles.find(v => String(v.id) === String(firstVehicleId)) || vehicles[0];
                            if (firstVehicle) {
                                selectVehicle(firstVehicle);
                                remainingCards[0].classList.add('selected');
                            }
                        } else {
                            // Hide details if no vehicles left
                            const detailsEl = document.getElementById('vehicleDetails');
                            if (detailsEl) detailsEl.style.display = 'none';
                        }

                        // Update vehicle limit notice
                        updateVehicleLimitNotice();

                        // Show success message
                        showToast('Vehicle deleted successfully');

                        // Verify deletion was successful
                        verifyVehicleDeletion(vehicleId);
                    })
                    .catch(error => {
                        if (isForcedLogoutInProgress) return;
                        console.error('Error during vehicle deletion:', error);
                        showToast(error?.message || 'Failed to delete vehicle', 'error');
                    })
                    .finally(() => {
                        if (loadingToast && loadingToast.remove) {
                            loadingToast.remove();
                        }
                    });
                }
            );
        }
    }
    
    // Show a custom confirmation modal instead of the browser's confirm dialog
    function showConfirmModal(title, message, confirmText, cancelText, onConfirm, onCancel) {
        // Check if modal already exists, remove it if it does
        const existingModal = document.getElementById('customConfirmModal');
        if (existingModal) {
            document.body.removeChild(existingModal);
        }
        
        // Create modal element
        const modal = document.createElement('div');
        modal.id = 'customConfirmModal';
        modal.className = 'custom-modal';
        
        // Create modal content
        modal.innerHTML = `
            <div class="custom-modal-content">
                <div class="custom-modal-header">
                    <h3>${title}</h3>
                    <button class="custom-modal-close">&times;</button>
                </div>
                <div class="custom-modal-body">
                    <p>${message}</p>
                </div>
                <div class="custom-modal-footer">
                    <button class="custom-modal-btn custom-modal-cancel">${cancelText || 'Cancel'}</button>
                    <button class="custom-modal-btn custom-modal-confirm">${confirmText || 'Confirm'}</button>
                </div>
            </div>
        `;
        
        // Add modal to body
        document.body.appendChild(modal);
        
        // Add event listeners
        const closeBtn = modal.querySelector('.custom-modal-close');
        const cancelBtn = modal.querySelector('.custom-modal-cancel');
        const confirmBtn = modal.querySelector('.custom-modal-confirm');
        
        const closeModal = () => {
            modal.classList.remove('active');
            setTimeout(() => {
                modal.remove();
            }, 300);
        };
        
        closeBtn.addEventListener('click', () => {
            closeModal();
            if (onCancel) onCancel();
        });
        
        cancelBtn.addEventListener('click', () => {
            closeModal();
            if (onCancel) onCancel();
        });
        
        confirmBtn.addEventListener('click', () => {
            closeModal();
            if (onConfirm) onConfirm();
        });
        
        // Add styles for the modal
        const style = document.createElement('style');
        style.textContent = `
            .custom-modal {
                position: fixed;
                top: 0;
                left: 0;
                width: 100%;
                height: 100%;
                background-color: rgba(0, 0, 0, 0.5);
                display: flex;
                justify-content: center;
                align-items: center;
                z-index: 9999;
                opacity: 0;
                transition: opacity 0.3s ease;
            }
            
            .custom-modal.active {
                opacity: 1;
            }
            
            .custom-modal-content {
                background-color: #fff;
                border-radius: 10px;
                box-shadow: 0 5px 15px rgba(0, 0, 0, 0.3);
                width: 90%;
                max-width: 500px;
                max-height: 90vh;
                overflow: auto;
                transform: translateY(-20px);
                transition: transform 0.3s ease;
            }
            
            .custom-modal.active .custom-modal-content {
                transform: translateY(0);
            }
            
            .custom-modal-header {
                padding: 15px 20px;
                border-bottom: 1px solid #eee;
                display: flex;
                justify-content: space-between;
                align-items: center;
            }
            
            .custom-modal-header h3 {
                margin: 0;
                color: #333;
                font-size: 20px;
            }
            
            .custom-modal-close {
                background: none;
                border: none;
                font-size: 24px;
                cursor: pointer;
                color: #999;
            }
            
            .custom-modal-body {
                padding: 20px;
                color: #666;
                font-size: 16px;
                line-height: 1.5;
            }
            
            .custom-modal-footer {
                padding: 15px 20px;
                border-top: 1px solid #eee;
                display: flex;
                justify-content: flex-end;
                gap: 10px;
            }
            
            .custom-modal-btn {
                padding: 10px 20px;
                border: none;
                border-radius: 5px;
                cursor: pointer;
                font-size: 14px;
                font-weight: bold;
                transition: background-color 0.2s;
            }
            
            .custom-modal-cancel {
                background-color: #f1f1f1;
                color: #555;
            }
            
            .custom-modal-cancel:hover {
                background-color: #e1e1e1;
            }
            
            .custom-modal-confirm {
                background-color: #dc3545;
                color: white;
            }
            
            .custom-modal-confirm:hover {
                background-color: #c82333;
            }
        `;
        document.head.appendChild(style);
        
        // Show modal with animation
        setTimeout(() => {
            modal.classList.add('active');
        }, 10);
        
        return modal;
    }

    // Show a simple alert modal
    function showAlertModal(title, message, buttonText, onClose, showButton = true) {
        // Remove any existing alert modals
        const existingModals = document.querySelectorAll('.custom-modal');
        existingModals.forEach(modal => {
            document.body.removeChild(modal);
        });
        
        // Create modal element
        const modal = document.createElement('div');
        modal.id = 'alertModal';
        modal.className = 'custom-modal';
        
        // Create button HTML only if showButton is true
        const buttonHtml = showButton ? 
            `<button class="custom-modal-btn custom-modal-confirm">${buttonText || 'OK'}</button>` : 
            '';
        
        // Create modal content
        modal.innerHTML = `
            <div class="custom-modal-content">
                <div class="custom-modal-header">
                    <h3>${title}</h3>
                    ${showButton ? '<button class="custom-modal-close">&times;</button>' : ''}
                </div>
                <div class="custom-modal-body">
                    ${message}
                </div>
                <div class="custom-modal-footer">
                    ${buttonHtml}
                </div>
            </div>
        `;
        
        // Add styles for modal
        const style = document.createElement('style');
        style.textContent = `
            .deletion-spinner {
                text-align: center;
                margin: 20px 0;
                font-size: 24px;
                color: #007BFF;
            }
            
            .custom-modal {
                position: fixed;
                top: 0;
                left: 0;
                width: 100%;
                height: 100%;
                background-color: rgba(0, 0, 0, 0.5);
                display: flex;
                align-items: center;
                justify-content: center;
                z-index: 1000;
                opacity: 0;
                visibility: hidden;
                transition: all 0.3s ease;
            }
            
            .custom-modal.active {
                opacity: 1;
                visibility: visible;
            }
            
            .custom-modal-content {
                background-color: white;
                border-radius: 8px;
                width: 90%;
                max-width: 500px;
                transform: translateY(-50px);
                transition: all 0.3s ease;
                overflow: hidden;
                box-shadow: 0 5px 15px rgba(0, 0, 0, 0.3);
            }
            
            .custom-modal.active .custom-modal-content {
                transform: translateY(0);
            }
            
            .custom-modal-header {
                padding: 15px;
                background: linear-gradient(135deg, #007BFF, #22568f);
                color: white;
                display: flex;
                justify-content: space-between;
                align-items: center;
            }
            
            .custom-modal-header h3 {
                margin: 0;
                font-size: 18px;
            }
            
            .custom-modal-close {
                background: none;
                border: none;
                color: white;
                font-size: 24px;
                cursor: pointer;
                outline: none;
                padding: 0;
                margin: 0;
            }
            
            .custom-modal-body {
                padding: 20px;
                font-size: 16px;
                color: #333;
                line-height: 1.5;
            }
            
            .custom-modal-footer {
                padding: 15px;
                display: flex;
                justify-content: flex-end;
                border-top: 1px solid #eee;
            }
            
            .custom-modal-btn {
                padding: 8px 16px;
                border: none;
                border-radius: 4px;
                cursor: pointer;
                transition: all 0.3s ease;
            }
            
            .custom-modal-confirm {
                background-color: #007BFF;
                color: white;
            }
            
            .custom-modal-confirm:hover {
                background-color: #0069d9;
            }
        `;
        document.head.appendChild(style);
        
        // Add modal to body
        document.body.appendChild(modal);
        
        // Add event listeners only if showButton is true
        if (showButton) {
            const closeBtn = modal.querySelector('.custom-modal-close');
            const confirmBtn = modal.querySelector('.custom-modal-confirm');
            
            const closeModal = () => {
                modal.classList.remove('active');
                setTimeout(() => {
                    if (document.body.contains(modal)) {
                    document.body.removeChild(modal);
                    }
                    if (document.head.contains(style)) {
                    document.head.removeChild(style);
                    }
                    if (onClose && typeof onClose === 'function') {
                        onClose();
                    }
                }, 300);
            };
            
            if (closeBtn) {
                closeBtn.addEventListener('click', closeModal);
            }
            
            if (confirmBtn) {
                confirmBtn.addEventListener('click', closeModal);
            }
        }
        
        // Show modal with animation
        setTimeout(() => {
            modal.classList.add('active');
        }, 10);
        
        // Return the modal element so it can be referenced
        return modal;
    }
    
    // Handle logout functionality
    function handleLogout() {
        showConfirmModal(
            'Confirm Logout',
            'Are you sure you want to logout?',
            'Logout',
            'Cancel',
            () => {
                // Create and show the animation
                const animationContainer = document.createElement('div');
                animationContainer.className = 'logout-animation';
                animationContainer.innerHTML = `
                    <div class="animation-icon"><i class="fas fa-sign-out-alt fa-bounce"></i></div>
                    <div class="animation-text">Logging out...</div>
                `;
                document.body.appendChild(animationContainer);
                
                // Add animation styles
                const style = document.createElement('style');
                style.textContent = `
                    .logout-animation {
                        position: fixed;
                        top: 0;
                        left: 0;
                        width: 100%;
                        height: 100%;
                        background-color: rgba(0,0,0,0.8);
                        display: flex;
                        flex-direction: column;
                        justify-content: center;
                        align-items: center;
                        z-index: 9999;
                        color: white;
                        opacity: 0;
                        transition: opacity 0.3s ease;
                    }
                    .logout-animation.show {
                        opacity: 1;
                    }
                    .animation-icon {
                        font-size: 50px;
                        margin-bottom: 20px;
                    }
                    .animation-text {
                        font-size: 24px;
                    }
                    .fa-bounce {
                        animation: fa-bounce 1s infinite;
                    }
                `;
                document.head.appendChild(style);
                
                // Show the animation
                setTimeout(() => {
                    animationContainer.classList.add('show');
                }, 10);
                
                // Clear authentication data and redirect after animation
                setTimeout(() => {
            localStorage.removeItem('isLoggedIn');
            localStorage.removeItem('userPhone');
            // Also remove userMembership to prevent it from affecting new users
            localStorage.removeItem('userMembership');
            if (window.AuthToken && typeof window.AuthToken.clear === 'function') {
                window.AuthToken.clear();
            } else {
                try { sessionStorage.removeItem('authToken'); } catch (_e) {}
                localStorage.removeItem('authToken');
            }
            window.location.href = 'index';
                }, 1500);
        }
        );
    }

    // Handle delete account functionality
    function handleDeleteAccount() {
        showConfirmModal(
            'Delete Account',
            'Are you sure you want to delete your account? This action cannot be undone and all your data will be permanently removed.',
            'Delete Account',
            'Cancel',
            () => {
                // Double confirmation
                showConfirmModal(
                    'Final Confirmation',
                    'Please confirm again that you want to delete your account. All your vehicles and profile data will be lost.',
                    'Yes, Delete My Account',
                    'No, Keep My Account',
                    () => {
                        // Create and show the animation
                        const animationContainer = document.createElement('div');
                        animationContainer.className = 'delete-animation';
                        animationContainer.innerHTML = `
                            <div class="animation-icon"><i class="fas fa-trash-alt fa-shake"></i></div>
                            <div class="animation-text">Deleting account...</div>
                            <div class="animation-text">We’re sad to see you go. You were a valuable part of our platform. Your data has been safely removed as per our privacy policy.</div>
                        `;
                        document.body.appendChild(animationContainer);
                        
                        // Add animation styles
                        const style = document.createElement('style');
                        style.textContent = `
                            .delete-animation {
                                position: fixed;
                                top: 0;
                                left: 0;
                                width: 100%;
                                height: 100%;
                                background-color: rgba(220,53,69,0.9);
                                display: flex;
                                flex-direction: column;
                                justify-content: center;
                                align-items: center;
                                z-index: 9999;
                                color: white;
                                opacity: 0;
                                transition: opacity 0.3s ease;
                            }
                            .delete-animation.show {
                                opacity: 1;
                            }
                            .animation-icon {
                                font-size: 50px;
                                margin-bottom: 20px;
                            }
                            .animation-text {
                                font-size: 24px;
                            }
                            .fa-shake {
                                animation: fa-shake 1s infinite;
                            }
                        `;
                        document.head.appendChild(style);
                        
                        // Show the animation
                        setTimeout(() => {
                            animationContainer.classList.add('show');
                        }, 10);
                        
                        // Get user phone number from localStorage
                        const userPhone = localStorage.getItem('userPhone');
                        
                        // Call API to delete user account
                        fetch(getApiUrl(`users/${userPhone}`), {
                            method: 'DELETE',
                            headers: {
                                'Content-Type': 'application/json'
                            }
                        })
                        .then(async response => {
                            // If session is invalid, force logout and stop.
                            if (response.status === 401 || response.status === 403) {
                                throw response;
                            }

                            let data = {};
                            try {
                                data = await response.json();
                            } catch (_e) {
                                data = {};
                            }

                            // If user is already deleted, treat as success and clear local session.
                            if (response.status === 404) {
                                return { success: true, message: 'Account already deleted' };
                            }

                            if (!response.ok) {
                                const msg = (data && data.message) ? data.message : `Delete failed (HTTP ${response.status})`;
                                throw new Error(msg);
                            }

                            return data;
                        })
                        .then(data => {
                            if (data && data.success) {
                                // Success! Account deleted
                                // Clear local storage
                                localStorage.removeItem('isLoggedIn');
                                localStorage.removeItem('userPhone');
                                // Also remove userMembership to prevent it from affecting new users
                                localStorage.removeItem('userMembership');
                                if (window.AuthToken && typeof window.AuthToken.clear === 'function') {
                                    window.AuthToken.clear();
                                } else {
                                    try { sessionStorage.removeItem('authToken'); } catch (_e) {}
                                    localStorage.removeItem('authToken');
                                }
                                
                                // Redirect after animation completes
                                setTimeout(() => {
                                    window.location.href = 'index';
                                }, 1000);
                            } else {
                                // Remove animation
                                document.body.removeChild(animationContainer);
                                if (document.head.contains(style)) {
                                    document.head.removeChild(style);
                                }
                                
                                console.error('Account deletion failed:', data.message);
                                // Show more informative error message
                                const errorDetails = data.message || 'Unknown error occurred';
                                showAlertModal('Account Deletion Failed', `There was a problem deleting your account: ${errorDetails}`, 'OK');
                            }
                        })
                        .catch(error => {
                            // If we threw the Response for auth failure above, it will be caught here.
                            if (error && typeof error.status === 'number') {
                                if (error.status === 401 || error.status === 403) {
                                    // Session invalid; clear local state and redirect.
                                    localStorage.removeItem('isLoggedIn');
                                    localStorage.removeItem('userPhone');
                                    localStorage.removeItem('userMembership');
                                    if (window.AuthToken && typeof window.AuthToken.clear === 'function') {
                                        window.AuthToken.clear();
                                    } else {
                                        try { sessionStorage.removeItem('authToken'); } catch (_e) {}
                                        localStorage.removeItem('authToken');
                                    }
                                    window.location.href = 'login?reason=session_expired';
                                    return;
                                }
                            }
                            // Remove animation
                            document.body.removeChild(animationContainer);
                            if (document.head.contains(style)) {
                                document.head.removeChild(style);
                            }
                            
                            console.error('Error deleting account:', error);
                            // Show more detailed error message with connection information
                            const errorMessage = error.message || 'Unknown error';
                            showAlertModal('Error', `Could not connect to the server. Please try again later. (Details: ${errorMessage})`, 'OK');
                        });
                    }
                );
            }
        );
    }

    // Initialize profile photo functionality
    function initProfilePhoto() {
        // This file currently contains multiple init() paths; guard against double-binding events.
        if (window.__hpgProfilePhotoInitDone) return;
        window.__hpgProfilePhotoInitDone = true;

        // Client-side image compression helper (strips EXIF/metadata by re-encoding)
        // - Enforces reasonable dimensions for faster upload + storage savings.
        // - Uses WebP when available, falls back to JPEG.
        async function compressImageFileForUpload(file, opts) {
            try {
                if (!file || !file.type || !String(file.type).startsWith('image/')) return file;

                const maxWidth = opts && opts.maxWidth ? opts.maxWidth : 1800;
                const maxHeight = opts && opts.maxHeight ? opts.maxHeight : 1800;
                const maxOutputBytes = opts && opts.maxOutputBytes ? opts.maxOutputBytes : null;
                const preferredQuality = typeof (opts && opts.quality) === 'number' ? opts.quality : 0.82;

                // Prefer WebP where supported
                const preferredTypes = ['image/webp', 'image/jpeg'];

                let bitmap;
                try {
                    // Try to respect EXIF orientation when supported
                    bitmap = await createImageBitmap(file, { imageOrientation: 'from-image' });
                } catch (_e) {
                    bitmap = await createImageBitmap(file);
                }

                const srcW = bitmap.width || 0;
                const srcH = bitmap.height || 0;
                if (!srcW || !srcH) {
                    try { bitmap.close(); } catch (_e) {}
                    return file;
                }

                const scale = Math.min(1, maxWidth / srcW, maxHeight / srcH);
                const outW = Math.max(1, Math.round(srcW * scale));
                const outH = Math.max(1, Math.round(srcH * scale));

                const canvas = document.createElement('canvas');
                canvas.width = outW;
                canvas.height = outH;
                const ctx = canvas.getContext('2d', { alpha: false });
                ctx.imageSmoothingEnabled = true;
                ctx.imageSmoothingQuality = 'high';
                ctx.drawImage(bitmap, 0, 0, outW, outH);
                try { bitmap.close(); } catch (_e) {}

                const canvasToBlob = (type, quality) => new Promise((resolve) => {
                    try {
                        canvas.toBlob((b) => resolve(b), type, quality);
                    } catch (_e) {
                        resolve(null);
                    }
                });

                const pickType = async () => {
                    for (const t of preferredTypes) {
                        const b = await canvasToBlob(t, preferredQuality);
                        if (b && b.size > 0) return t;
                    }
                    return 'image/jpeg';
                };

                const mimeType = await pickType();

                let quality = preferredQuality;
                let blob = await canvasToBlob(mimeType, quality);
                if (!blob || blob.size === 0) return file;

                if (maxOutputBytes && blob.size > maxOutputBytes) {
                    // Iteratively reduce quality to meet size cap
                    for (let i = 0; i < 8 && blob.size > maxOutputBytes; i++) {
                        quality = Math.max(0.5, quality - 0.07);
                        const next = await canvasToBlob(mimeType, quality);
                        if (!next || next.size === 0) break;
                        blob = next;
                    }
                }

                // If compression didn't help, keep original
                if (blob.size >= file.size) return file;

                const baseName = (file.name || 'image').replace(/\.[^.]+$/, '');
                const ext = mimeType === 'image/webp' ? 'webp' : 'jpg';
                return new File([blob], `${baseName}-compressed.${ext}`, { type: mimeType });
            } catch (e) {
                console.warn('Image compression failed; uploading original', e);
                return file;
            }
        }

        const userAvatar = document.getElementById('userAvatar');
        const photoModal = document.getElementById('profilePhotoModal');
        const photoViewer = document.getElementById('profilePhotoViewer');
        const closeUploadModal = document.getElementById('closeUploadModal');
        const closePhotoViewer = document.getElementById('closePhotoViewer');
        const cameraBtnOption = document.getElementById('cameraBtnOption');
        const galleryBtnOption = document.getElementById('galleryBtnOption');
        const photoFileInput = document.getElementById('photoFileInput');
        const photoPreview = document.getElementById('photoPreview');
        const savePhotoBtn = document.getElementById('savePhotoBtn');
        const removePhotoBtn = document.getElementById('removePhotoBtn');
        const changeProfilePhoto = document.getElementById('changeProfilePhoto');
        const removeProfilePhoto = document.getElementById('removeProfilePhoto');
        const viewerPhoto = document.getElementById('viewerPhoto');

        let currentPhotoFile = null;

        // Open upload modal when clicking on avatar
        userAvatar.addEventListener('click', function() {
            if (userData.profilePhoto) {
                // If user already has a photo, show the viewer
                viewerPhoto.src = userData.profilePhoto;
                photoViewer.classList.add('active');
            } else {
                // If no photo, show the upload modal
                photoModal.classList.add('active');
            }
        });

        // Close modals
        closeUploadModal.addEventListener('click', function() {
            photoModal.classList.remove('active');
            resetPhotoPreview();
        });

        closePhotoViewer.addEventListener('click', function() {
            photoViewer.classList.remove('active');
        });

        // Camera option (simulated in web)
        cameraBtnOption.addEventListener('click', function() {
            // In a real mobile app, this would open the camera
            // For web demo, we'll just trigger the file input
            photoFileInput.click();
        });

        // Gallery option
        galleryBtnOption.addEventListener('click', function() {
            photoFileInput.click();
        });

        // Handle file selection
        photoFileInput.addEventListener('change', function(e) {
            if (e.target.files && e.target.files[0]) {
                const file = e.target.files[0];
                
                // Validate file (only images, max 5MB)
                if (!file.type.match('image.*')) {
                    showAlertModal('Invalid File', 'Please select an image file', 'OK');
                    return;
                }
                
                if (file.size > 5 * 1024 * 1024) {
                    showAlertModal('File Too Large', 'Image size should be less than 5MB', 'OK');
                    return;
                }
                
                currentPhotoFile = file;
                
                // Preview the image
                const reader = new FileReader();
                reader.onload = function(e) {
                    photoPreview.innerHTML = `<img src="${e.target.result}" alt="Profile Preview">`;
                    photoPreview.classList.add('has-image');
                    removePhotoBtn.style.display = 'block';
                };
                reader.readAsDataURL(file);
            }
        });

        // Helper function to check if API is reachable
        // NOTE: "reachable" means the browser got *any* HTTP response.
        // 401/403/404/405 still indicate the server is up; only network/timeout means unreachable.
        async function isApiReachable() {
            const controller = new AbortController();
            const timeoutId = setTimeout(() => controller.abort(), 5000); // 5 second timeout

            try {
                try {
                    // Prefer GET: some servers/proxies block HEAD with 405.
                    const response = await fetch(getApiUrl('health-check'), {
                        method: 'GET',
                        cache: 'no-store',
                        signal: controller.signal
                    });

                    // Any HTTP response means the server was reached.
                    if (response) return true;
                } catch (e) {
                    console.log('Health check failed, trying users endpoint');
                }

                const userPhone = localStorage.getItem('userPhone');
                if (userPhone) {
                    const response = await fetch(getApiUrl(`users/${userPhone}`), {
                        method: 'GET',
                        cache: 'no-store',
                        signal: controller.signal
                    });

                    return !!response;
                }

                return false;
            } catch (error) {
                console.error('API connectivity check failed:', error);
                return false;
            } finally {
                clearTimeout(timeoutId);
            }
        }

        // Save photo
        savePhotoBtn.addEventListener('click', async function() {
            if (currentPhotoFile) {
                // Show loading state
                savePhotoBtn.disabled = true;
                savePhotoBtn.innerHTML = '<i class="fas fa-spinner fa-spin"></i> Uploading...';
                
                // First check if API is reachable
                const apiReachable = await isApiReachable();
                if (!apiReachable) {
                    savePhotoBtn.disabled = false;
                    savePhotoBtn.innerHTML = '<i class="fas fa-check"></i> Save Photo';
                    
                    // Show detailed connection error with retry option
                    showConfirmModal(
                        'Connection Error',
                        'We could not reach the server right now. Please check your internet connection and try again in a moment.',
                        'Retry',
                        'Cancel',
                        () => {
                            // Retry the upload when user clicks retry
                            setTimeout(() => savePhotoBtn.click(), 500);
                        },
                        () => {
                            // Show alternative options when user cancels
                            showAlertModal(
                                'Server Unavailable',
                                'The server is temporarily unavailable. Please try again after some time.',
                                'OK'
                            );
                        }
                    );
                    return;
                }
                
                // Compress before upload (profile photo is only used in dashboard; keep it small)
                const fileToUpload = await compressImageFileForUpload(currentPhotoFile, {
                    maxWidth: 512,
                    maxHeight: 512,
                    quality: 0.72,
                    maxOutputBytes: 300 * 1024
                });

                // Create form data for API
                const formData = new FormData();
                formData.append('photo', fileToUpload);
                
                // Get user phone number from localStorage
                const userPhone = localStorage.getItem('userPhone');
                
                try {
                    // Use fetchWithRetry instead of regular fetch
                    const response = await fetchWithRetry(getApiUrl(`users/${userPhone}/profile-photo`), {
                    method: 'POST',
                    body: formData
                    }, 3);
                    
                    const data = await response.json();
                    
                    if (data.success) {
                        // Update UI with the photo URL from the server
                        updateProfilePhoto(data.photoUrl);
                    photoModal.classList.remove('active');
                    resetPhotoPreview();
                    showToast('Profile photo updated successfully!');
            } else {
                        throw new Error(data.message || 'Failed to upload photo');
                    }
                } catch (error) {
                    console.error('Error uploading photo:', error);
                    let errorMessage = 'Could not upload your photo right now. ';
                    
                    if (error.message.includes('NetworkError') || 
                        error.message.includes('Failed to fetch') ||
                        error.message.includes('Network request failed')) {
                        errorMessage += 'Please check your internet connection and try again.';
                    } else if (error.message.includes('status: 403') || 
                               error.message.includes('Forbidden')) {
                        errorMessage += 'Please try again later.';
                    } else if (error.message.includes('CORS')) {
                        errorMessage += 'Please try again later.';
                    } else {
                        // Keep technical details out of the user message; log is already in console.
                        errorMessage += 'Please try again later.';
                    }
                    
                    // Show error with more details and server status
                    showConfirmModal(
                        'Upload Error', 
                        `${errorMessage}<br><br>Would you like to try again?`,
                        'Retry',
                        'Cancel',
                        () => {
                            // Retry the upload
                            setTimeout(() => savePhotoBtn.click(), 500);
                        }
                    );
                } finally {
                    // Reset button state
                    savePhotoBtn.disabled = false;
                    savePhotoBtn.innerHTML = '<i class="fas fa-check"></i> Save Photo';
                }
            } else {
                showAlertModal('No Photo Selected', 'Please select an image first', 'OK');
            }
        });

        // Remove photo from preview
        removePhotoBtn.addEventListener('click', function() {
            resetPhotoPreview();
        });

        // Change photo from viewer
        changeProfilePhoto.addEventListener('click', function() {
            photoViewer.classList.remove('active');
            photoModal.classList.add('active');
        });

        // Remove profile photo
        removeProfilePhoto.addEventListener('click', function() {
            showConfirmModal(
                'Remove Profile Photo',
                'Are you sure you want to remove your profile photo?',
                'Remove',
                'Cancel',
                () => {
                    // Show loading state
                    removeProfilePhoto.disabled = true;
                    removeProfilePhoto.innerHTML = '<i class="fas fa-spinner fa-spin"></i> Removing...';
                    
                    // Get user phone number from localStorage
                    const userPhone = localStorage.getItem('userPhone');
                    
                    // Delete from server using async/await with fetchWithRetry
                    (async () => {
                        try {
                            const response = await fetchWithRetry(getApiUrl(`users/${userPhone}/profile-photo`), {
                        method: 'DELETE'
                            }, 3);
                            
                            const data = await response.json();
                            
                        if (data.success) {
                            // Update UI
                const userAvatar = document.getElementById('userAvatar');
                const imgElement = userAvatar.querySelector('img');
                if (imgElement) {
                    userAvatar.removeChild(imgElement);
                }
                
                // Show the default avatar icon
                if (defaultAvatar) {
                    defaultAvatar.style.display = 'block';
                }
                
                // Update user data
                userData.profilePhoto = null;
                
                // Close viewer
                photoViewer.classList.remove('active');
                
                // Show success message
                showToast('Profile photo removed');
                        } else {
                            showAlertModal('Removal Failed', data.message || 'Failed to remove photo', 'OK');
                        }
                        } catch (error) {
                        console.error('Error removing photo:', error);
                        showAlertModal('Removal Error', 'Failed to remove photo. Please try again.', 'OK');
                        } finally {
                        // Reset button state
                        removeProfilePhoto.disabled = false;
                        removeProfilePhoto.innerHTML = '<i class="fas fa-trash"></i> Remove Photo';
                        }
                    })();
                }
            );
        });

        // Reset photo preview
        function resetPhotoPreview() {
            photoPreview.innerHTML = '<i class="fas fa-user"></i>';
            photoPreview.classList.remove('has-image');
            photoFileInput.value = '';
            currentPhotoFile = null;
            removePhotoBtn.style.display = 'none';
        }
    }

    // Show toast notification
    function showToast(message, type = 'success', autoDismiss = true, duration = 3000) {
        const toast = document.createElement('div');
        toast.className = `toast ${type}`;
        toast.textContent = message;
        
        // Style the toast
        toast.style.position = 'fixed';
        toast.style.bottom = '20px';
        toast.style.left = '50%';
        toast.style.transform = 'translateX(-50%)';
        toast.style.padding = '12px 24px';
        toast.style.borderRadius = '8px';
        
        // Set background color based on toast type
        let backgroundColor;
        switch(type) {
            case 'error':
                backgroundColor = '#f44336';
                break;
            case 'info':
                backgroundColor = '#2196F3';
                break;
            case 'warning':
                backgroundColor = '#ff9800';
                break;
            default: // success
                backgroundColor = '#4CAF50';
        }
        
        toast.style.backgroundColor = backgroundColor;
        toast.style.color = 'white';
        toast.style.zIndex = '1000';
        toast.style.boxShadow = '0 2px 10px rgba(0,0,0,0.2)';
        
        // Add animation
        let style;
        if (autoDismiss) {
            // Calculate animation percentages based on duration
            const fadeInPercent = Math.min(15, 500 / duration * 100);
            const fadeOutStartPercent = Math.max(85, 100 - 500 / duration * 100);
            
            toast.style.animation = `fadeInOut ${duration/1000}s forwards`;
            style = document.createElement('style');
            style.textContent = `
                @keyframes fadeInOut {
                    0% { opacity: 0; transform: translate(-50%, 20px); }
                    ${fadeInPercent}% { opacity: 1; transform: translate(-50%, 0); }
                    ${fadeOutStartPercent}% { opacity: 1; transform: translate(-50%, 0); }
                    100% { opacity: 0; transform: translate(-50%, -20px); }
                }
            `;
            document.head.appendChild(style);
        } else {
            toast.style.animation = 'fadeIn 0.3s forwards';
            style = document.createElement('style');
            style.textContent = `
                @keyframes fadeIn {
                    0% { opacity: 0; transform: translate(-50%, 20px); }
                    100% { opacity: 1; transform: translate(-50%, 0); }
                }
            `;
            document.head.appendChild(style);
            
            // Add close button for persistent toasts
            const closeBtn = document.createElement('span');
            closeBtn.innerHTML = '&times;';
            closeBtn.style.marginLeft = '10px';
            closeBtn.style.cursor = 'pointer';
            closeBtn.style.fontWeight = 'bold';
            closeBtn.style.fontSize = '18px';
            closeBtn.onclick = function() {
                document.body.removeChild(toast);
                document.head.removeChild(style);
            };
            toast.appendChild(closeBtn);
        }
        
        document.body.appendChild(toast);
        
        // Remove toast after animation if auto-dismiss is true
        if (autoDismiss) {
            setTimeout(() => {
                if (document.body.contains(toast)) {
                    document.body.removeChild(toast);
                }
                if (document.head.contains(style)) {
                    document.head.removeChild(style);
                }
            }, duration);
        }
        
        // Return toast element so it can be removed programmatically
        return toast;
    }

    // Initialize tabs
    function initTabs() {
        // Get all tab buttons and content
        const tabButtons = document.querySelectorAll('.dashboard-nav a');
        const tabContents = document.querySelectorAll('.tab-content');
        
        // Add click event to each tab button
        tabButtons.forEach(button => {
            button.addEventListener('click', function(e) {
                e.preventDefault();
                
                // Get the tab id from the button's id
                const tabId = this.id.replace('Btn', '');
                
                // Switch to that tab
                switchTab(tabId);
            });
        });
    }

    // Switch tab function
    function switchTab(tabId) {
        // Remove active class from all tabs and buttons
        document.querySelectorAll('.tab-content').forEach(tab => {
            tab.classList.remove('active');
        });
        
        document.querySelectorAll('.dashboard-nav a').forEach(button => {
            button.classList.remove('active');
        });
        
        // Add active class to selected tab and button
        document.getElementById(tabId).classList.add('active');

        // If settings tab is opened, (re)initialize email section UI
        if (tabId === 'settingsTab') {
            try { setupEmailSettingsSection(); } catch (e) { console.warn('Email section init on tab switch failed', e); }
        }
        document.getElementById(tabId + 'Btn').classList.add('active');
    }
    
    // Show Premium Plans Modal
    function showPremiumPlansModal() {
        const modal = document.getElementById('premiumPlansModal');
        
        // First make the modal visible but keep content scaled down
        modal.classList.add('active');
        
        // Force browser to recognize the modal is active before animating content
        setTimeout(() => {
            // Content will automatically zoom in due to CSS transition
            console.log("Premium plans modal opened with zoom animation");
        }, 10);
    }
    
    // Handle Premium Plan Selection
    function handlePlanSelection(plan, price) {
        // Show confirmation modal
        showConfirmModal(
            'Confirm Subscription',
            `You are about to subscribe to the ${plan} plan for ₹${price}. Do you want to proceed?`,
            'Proceed to Payment',
            'Cancel',
            () => {
                // This would be replaced with actual payment gateway integration
                processPremiumPayment(plan, price);
            },
            () => {
                // User cancelled, do nothing
            }
        );
    }
    
    // Process Premium Payment with Razorpay integration (secure backend-assisted flow)
    function processPremiumPayment(plan, price) {
        // Show loading state
        showAlertModal('Processing Payment', 'Please wait while we set up the payment...', '', null, false);
        
        // Get user contact number from localStorage
        const userPhone = localStorage.getItem('userPhone');
        if (!userPhone) {
            showAlertModal('Error', 'User information not found. Please log in again.', 'OK', () => {
                window.location.href = 'login';
            });
            return;
        }
        
        // Close any open modal before opening Razorpay
        document.querySelectorAll('.modal').forEach(modal => {
            if (modal.classList.contains('active')) {
                modal.classList.remove('active');
            }
        });

        // 1) Ask backend to create an order securely (server decides amount from plan)
        fetch(getApiUrl('payments/create-order'), {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                plan: plan,
                currency: 'INR',
                receipt: `premium_${userPhone}_${Date.now()}`
            })
        })
        .then(res => res.json())
        .then(data => {
            if (!data || !data.success || !data.order) {
                throw new Error(data && data.message ? data.message : 'Failed to create order');
            }

            const order = data.order;
            const keyId = data.keyId; // public key from server

        const options = {
                key: keyId,
                amount: order.amount,
                currency: order.currency,
                name: 'Herapherigoods',
            description: `${plan} Premium Plan Subscription`,
                image: 'attached_assets/images/new-logo.webp',
                order_id: order.id,
            prefill: {
                    name: userData.name || '',
                contact: userPhone,
                    email: userData.email || ''
            },
                theme: { color: '#007BFF' },
            modal: {
                    ondismiss: function () {
                        showAlertModal('Payment Cancelled', 'You have cancelled the payment process. You can try again anytime.', 'OK');
                    }
                },
                handler: function (response) {
                    // 2) Upgrade membership only with server-side payment verification
                    updateMembershipOnServer(plan, response);
                }
            };

            try {
                if (window.Razorpay) {
                    const rzp = new window.Razorpay(options);
                    rzp.open();
            } else {
                    console.error('Razorpay SDK not loaded');
                    showAlertModal('Payment Error', 'Payment gateway not loaded. Please refresh and try again.', 'OK');
            }
        } catch (error) {
                console.error('Error initializing Razorpay:', error);
                showAlertModal('Payment Error', 'Unable to open payment gateway. Please try again.', 'OK');
        }
        })
        .catch(err => {
            console.error('Order create error:', err);
            showAlertModal('Payment Error', 'Unable to initiate payment. Please try again later.', 'OK');
        });
    }
    
    // Helper function to get plan price based on plan type
    function getPlanPrice(plan) {
        switch (plan.toLowerCase()) {
            case 'monthly':
                return '59';
            case 'quarterly':
                return '159';
            case 'half-yearly':
                return '299';
            case 'yearly':
                return '499';
            default:
                return '';
        }
    }
    
    // Update membership status on server
    function updateMembershipOnServer(plan, razorpayResponse) {
        // Get user phone from localStorage
        const userPhone = localStorage.getItem('userPhone');
        if (!userPhone) {
            showAlertModal('Error', 'User information not found. Please log in again.', 'OK', () => {
                window.location.href = 'login';
            });
            return;
        }
        
        // Prepare request data
        const requestData = {
            plan: plan,
            paymentId: razorpayResponse && razorpayResponse.razorpay_payment_id ? razorpayResponse.razorpay_payment_id : '',
            razorpay_order_id: razorpayResponse && razorpayResponse.razorpay_order_id ? razorpayResponse.razorpay_order_id : '',
            razorpay_signature: razorpayResponse && razorpayResponse.razorpay_signature ? razorpayResponse.razorpay_signature : ''
        };
        
        // Make API call to update membership
        fetch(getApiUrl(`users/${userPhone}/upgrade-premium`), {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify(requestData)
        })
        .then(response => {
            if (!response.ok) {
                throw new Error(`Server responded with status: ${response.status}`);
            }
            return response.json();
        })
        .then(data => {
            console.log("Membership update response:", data);
            
            if (data.success) {
                // Update local user data
                userData.membership = 'Premium';
                userData.maxVehicles = 5;
                userData.membershipPurchaseTime = data.purchaseTime;
                userData.membershipExpireTime = data.expireTime;
                userData.membershipPlan = plan;
                userData.membershipPrice = data.price || getPlanPrice(plan);
                
                // Update UI
                const membershipBadge = document.getElementById('membershipBadge');
                if (membershipBadge) {
                    membershipBadge.textContent = 'Premium';
                    membershipBadge.style.backgroundColor = '#FFD700';
                }
                
                // Update vehicle limit notice
                updateVehicleLimitNotice();
                
                // Toggle premium sections visibility
                togglePremiumSections();
                
                // Save premium status to localStorage
                localStorage.setItem('userMembership', 'Premium');
                
                // Show success message
                showAlertModal(
                    'Payment Successful',
                    `You have successfully upgraded to the ${plan} Premium plan. Your account has been updated with premium features.`,
                    'OK',
                    () => {
                        // Close the premium plans modal
                        const modal = document.getElementById('premiumPlansModal');
                        if (modal) {
                            modal.classList.add('closing');
                            
                            // Wait for animation to complete before hiding the modal
                            setTimeout(() => {
                                modal.classList.remove('active');
                                modal.classList.remove('closing');
                            }, 300);
                        }
                    }
                );
            } else {
                // Show error message
                showAlertModal(
                    'Update Failed',
                    'There was a problem updating your membership. Please try again or contact support.',
                    'OK'
                );
            }
        })
        .catch(error => {
            console.error("Error updating membership:", error);
            
            // Show error message
            showAlertModal(
                'Update Failed',
                'There was a problem updating your membership. Please try again or contact support.',
                'OK'
            );
        });
    }
    
    // Toggle notification setting
    function toggleNotificationSetting(type, isEnabled) {
        console.log(`${type} notifications ${isEnabled ? 'enabled' : 'disabled'}`);
        
        // Show toast notification
        showToast(`${type.charAt(0).toUpperCase() + type.slice(1)} notifications ${isEnabled ? 'enabled' : 'disabled'}`);
        
        // Update toggle switch color
        const toggleId = `${type}NotificationsToggle`;
        const toggleElement = document.getElementById(toggleId);
        
        if (toggleElement) {
            // The slider color is controlled by CSS based on the checked state
            // We're just ensuring the checked state matches the isEnabled parameter
            toggleElement.checked = isEnabled;
        }
        
        // In a real implementation, this would make an API call to update user preferences
    }

    // Initialize event listeners
    function initEventListeners() {
        // Add Vehicle button
        document.getElementById('addVehicleBtn').addEventListener('click', handleAddVehicle);
        
        // Edit Vehicle button
        document.getElementById('editVehicleBtn').addEventListener('click', handleEditVehicle);
        
        // Delete Vehicle button
        document.getElementById('deleteVehicleBtn').addEventListener('click', handleDeleteVehicle);
        
        // Initialize tabs
        initTabs();
        
        // Add account management buttons to the settings tab
        const settingsTab = document.getElementById('settingsTab');
        if (settingsTab) {
            // The settings tab is now populated with HTML from the HTML file
            // We just need to add event listeners to the buttons
            
            // Upgrade to Premium button
            const upgradeBtn = document.getElementById('upgradeToPremiumBtn');
            if (upgradeBtn) {
                upgradeBtn.addEventListener('click', showPremiumPlansModal);
            }
            
            // Close Premium Plans Modal
            const closePremiumModal = document.getElementById('closePremiumModal');
            if (closePremiumModal) {
                closePremiumModal.addEventListener('click', function() {
                    const modal = document.getElementById('premiumPlansModal');
                    // Add a class to trigger the zoom-out animation
                    modal.classList.add('closing');
                    
                    // Wait for animation to complete before hiding the modal
                    setTimeout(() => {
                        modal.classList.remove('active');
                        modal.classList.remove('closing');
                    }, 300);
                });
            }
            
            // Select Plan buttons
            document.querySelectorAll('.select-plan-btn').forEach(button => {
                button.addEventListener('click', function() {
                    const plan = this.getAttribute('data-plan');
                    const price = this.getAttribute('data-price');
                    handlePlanSelection(plan, price);
                });
            });
            
            // My Plans section buttons
            const renewPlanBtn = document.getElementById('renewPlanBtn');
            if (renewPlanBtn) {
                renewPlanBtn.addEventListener('click', function() {
                    // Show the premium plans modal for renewal
                    showPremiumPlansModal();
                });
            }
            
            // Settings buttons
            const logoutBtn = document.getElementById('logoutBtn');
            if (logoutBtn) {
                logoutBtn.addEventListener('click', handleLogout);
            }
            
            const deleteAccountBtn = document.getElementById('deleteAccountBtn');
            if (deleteAccountBtn) {
                deleteAccountBtn.addEventListener('click', handleDeleteAccount);
            }
            
            const editProfileBtn = document.getElementById('editProfileBtn');
            if (editProfileBtn) {
                editProfileBtn.addEventListener('click', function() {
                    // First switch to the vehicles tab
                    switchTab('vehiclesTab');
                    
                    // Then trigger the edit vehicle functionality
                    // We need to make sure a vehicle is selected first
                    const vehicleList = document.getElementById('vehicleList');
                    if (vehicleList && vehicleList.children.length > 0) {
                        // Select the first vehicle if none is selected
                        const selectedVehicle = vehicleList.querySelector('.vehicle-card.selected');
                        if (!selectedVehicle && vehicleList.children[0]) {
                            // Simulate a click on the first vehicle card
                            vehicleList.children[0].click();
                        }
                        
                        // Now trigger the edit functionality
                        setTimeout(() => {
                            handleEditVehicle();
                        }, 100);
                    } else {
                        // No vehicles available
                        showAlertModal('No Vehicles', 'You need to add a vehicle first before you can edit your profile.', 'OK');
                    }
                });
            }
            
            // Toggle switches for notifications
            const emailNotificationsToggle = document.getElementById('emailNotificationsToggle');
            if (emailNotificationsToggle) {
                emailNotificationsToggle.addEventListener('change', function() {
                    // Handle email notifications toggle
                    const isEnabled = this.checked;
                    toggleNotificationSetting('email', isEnabled);
                });
            }
            
            const smsNotificationsToggle = document.getElementById('smsNotificationsToggle');
            if (smsNotificationsToggle) {
                smsNotificationsToggle.addEventListener('change', function() {
                    // Handle SMS notifications toggle
                    const isEnabled = this.checked;
                    toggleNotificationSetting('sms', isEnabled);
                });
            }
        }
    }

    // Function to validate and format phone number inputs
    function setupPhoneValidation() {
        const phoneInputs = [
            document.getElementById('editContact'),
            document.getElementById('editWhatsapp'),
            document.getElementById('editAlternateContact')
        ];
        
        phoneInputs.forEach(input => {
            if (!input) return;
            
            // Create validation message element
            const validationMessage = document.createElement('div');
            validationMessage.className = 'validation-message';
            validationMessage.textContent = 'Please enter a valid 10-digit number';
            input.parentNode.appendChild(validationMessage);
            
            // Add hint for WhatsApp and alternate contact
            if (input.id === 'editWhatsapp' || input.id === 'editAlternateContact') {
                const optionalHint = document.createElement('small');
                optionalHint.className = 'detail-hint';
                optionalHint.textContent = 'This field is optional';
                input.parentNode.appendChild(optionalHint);
            }
            
            // Add input event listener
            input.addEventListener('input', function(e) {
                // Remove non-digits
                this.value = this.value.replace(/\D/g, '');
                
                // Limit to 10 digits
                if (this.value.length > 10) {
                    this.value = this.value.slice(0, 10);
                }
                
                // Show/hide validation message
                const isValid = this.value === '' || /^\d{10}$/.test(this.value);
                this.classList.toggle('invalid-input', !isValid);
                validationMessage.classList.toggle('show', !isValid);
            });
        });
    }

    // Initialize everything
    function init() {
        // Only initialize if authenticated
        if (checkAuth()) {
            // REMOVED: Don't check localStorage for membership status
            // The real membership status will be fetched from the database in initUserInfo()
            
            initUserInfo();
            initTabs();
            initEventListeners();
            initProfilePhoto();
            initServiceHighlights();
            
            // Try to upload any temporary profile photo if the server is now available
            checkAndUploadTempProfilePhoto();
            
            // Note: We've moved the vehicle initialization to after 
            // the user data is loaded in the initUserInfo function
            
            // Check if we have a previously selected vehicle ID in localStorage
            const lastSelectedVehicleId = localStorage.getItem('lastSelectedVehicleId');
            if (lastSelectedVehicleId) {
                console.log('Found previously selected vehicle ID:', lastSelectedVehicleId);
                // We'll use this in the fetchVehiclesData callback to select the correct vehicle
            }
        }
    }
    
    // Check for and upload any temporarily stored profile photo
    async function checkAndUploadTempProfilePhoto() {
        // Check if we have a temporary profile photo
        const tempPhotoUrl = localStorage.getItem('tempProfilePhoto');
        if (!tempPhotoUrl) {
            return; // No temporary photo to upload
        }
        
        console.log("Found temporarily stored profile photo. Checking if server is available...");
        
        // Check if the server is now available
        const apiReachable = await isApiReachable();
        if (!apiReachable) {
            console.log("Server still unavailable. Will try again later.");
            return; // Server still unavailable
        }
        
        try {
            console.log("Server available. Attempting to upload temporary profile photo...");
            
            // Convert data URL to File object
            const response = await fetch(tempPhotoUrl);
            const blob = await response.blob();
            const file = new File([blob], "profile-photo.jpg", { type: blob.type });

            // Re-compress before upload (also strips metadata)
            const fileToUpload = await (async () => {
                try {
                    if (!file || !file.type || !String(file.type).startsWith('image/')) return file;
                    // local helper: use createImageBitmap + canvas re-encode
                    let bitmap;
                    try { bitmap = await createImageBitmap(file, { imageOrientation: 'from-image' }); } catch (_e) { bitmap = await createImageBitmap(file); }
                    const maxDim = 512;
                    const scale = Math.min(1, maxDim / bitmap.width, maxDim / bitmap.height);
                    const w = Math.max(1, Math.round(bitmap.width * scale));
                    const h = Math.max(1, Math.round(bitmap.height * scale));
                    const canvas = document.createElement('canvas');
                    canvas.width = w; canvas.height = h;
                    const ctx = canvas.getContext('2d', { alpha: false });
                    ctx.imageSmoothingEnabled = true;
                    ctx.imageSmoothingQuality = 'high';
                    ctx.drawImage(bitmap, 0, 0, w, h);
                    try { bitmap.close(); } catch (_e) {}
                    const toBlob = (type, quality) => new Promise((resolve) => canvas.toBlob(resolve, type, quality));
                    let type = 'image/webp';
                    let b = await toBlob(type, 0.72);
                    if (!b || !b.size) { type = 'image/jpeg'; b = await toBlob(type, 0.72); }
                    if (!b || !b.size) return file;
                    // Cap around 300KB
                    const cap = 300 * 1024;
                    let q = 0.72;
                    for (let i = 0; i < 8 && b.size > cap; i++) {
                        q = Math.max(0.5, q - 0.07);
                        const nb = await toBlob(type, q);
                        if (!nb || !nb.size) break;
                        b = nb;
                    }
                    if (b.size >= file.size) return file;
                    const ext = type === 'image/webp' ? 'webp' : 'jpg';
                    return new File([b], `profile-photo-compressed.${ext}`, { type });
                } catch (e) {
                    console.warn('Temp profile compression failed; uploading original', e);
                    return file;
                }
            })();
            
            // Get user phone number from localStorage
            const userPhone = localStorage.getItem('userPhone');
            
            // Create form data
            const formData = new FormData();
            formData.append('photo', fileToUpload);
            
            // Upload the photo
            const serverResponse = await fetchWithRetry(getApiUrl(`users/${userPhone}/profile-photo`), {
                method: 'POST',
                body: formData
            }, 3);
            
            const data = await serverResponse.json();
            
            if (data.success) {
                console.log("Successfully uploaded temporary profile photo to server");
                // Remove the temporary photo from localStorage
                localStorage.removeItem('tempProfilePhoto');
                // Update the profile photo with the URL from the server
                updateProfilePhoto(data.photoUrl);
                // Show success message
                showToast('Profile photo uploaded to server successfully', 'success');
            } else {
                console.error("Failed to upload temporary profile photo:", data.message);
            }
        } catch (error) {
            console.error("Error uploading temporary profile photo:", error);
        }
    }

    // Start the application with error handling
    try {
    init();
    } catch (error) {
        console.error("Error initializing dashboard:", error);
        showToast("An error occurred while loading the dashboard. Reloading the page...", "error");
        // Force reload after a short delay
        setTimeout(() => {
            window.location.href = window.location.href.split('#')[0];
        }, 3000);
    }

    /**
     * Fetches vehicles data from the API
     */
    function fetchVehiclesData() {
        if (USE_MOCK_DATA) {
            // Use mock data for testing
            console.log("Using mock vehicles data");
            // Mock data is defined above
            vehicles = MOCK_VEHICLES;
            initVehicleList();
            
            // Check if we have a previously selected vehicle ID in localStorage
            const lastSelectedVehicleId = localStorage.getItem('lastSelectedVehicleId');
            
            if (vehicles.length > 0) {
                if (lastSelectedVehicleId) {
                    // Try to find the previously selected vehicle
                    const previouslySelectedVehicle = vehicles.find(v => v.id == lastSelectedVehicleId);
                    if (previouslySelectedVehicle) {
                        // Select the previously selected vehicle
                        selectVehicle(previouslySelectedVehicle);
                        console.log('Selected previously selected vehicle:', previouslySelectedVehicle.id);
                        return;
                    }
                }
                // If no previously selected vehicle or it wasn't found, select the first one
                selectVehicle(vehicles[0]);
            } else {
                document.getElementById('vehicleDetails').style.display = 'none';
            }
            return;
        }
        
        // Show loading state
        const vehicleList = document.getElementById('vehicleList');
        const loadingIndicator = document.createElement('div');
        loadingIndicator.id = 'vehiclesLoadingIndicator';
        loadingIndicator.className = 'loading-indicator';
        loadingIndicator.innerHTML = '<i class="fas fa-spinner fa-spin"></i> Loading vehicles...';
        vehicleList.innerHTML = '';
        vehicleList.appendChild(loadingIndicator);
        
        // Use userPhone from localStorage
        const userPhone = localStorage.getItem('userPhone');
        if (!userPhone) {
            console.error('User phone number not found in localStorage');
            showErrorState('Authentication Error', 'Could not authenticate your session. Please try logging in again.');
            return;
        }
        
        console.log("Fetching vehicles for user:", userPhone);
        
        // Real API call when mock is disabled
        fetch(getApiUrl(`users/${userPhone}/vehicles`), {
            method: 'GET',
            headers: {
                'Content-Type': 'application/json'
            }
        })
        .then(response => {
            if (!response.ok) {
                if (maybeForceLogoutOnAuthError(response)) {
                    throw new Error('Auth invalid; redirecting');
                }
                throw new Error(`Server responded with status: ${response.status}`);
            }
            return response.json();
        })
        .then(data => {
            console.log("Vehicles data received:", data);
            
            if (data && Array.isArray(data.vehicles)) {
                // Update vehicles array with fetched data
                vehicles = data.vehicles;
                
                // Check for any cached vehicle data in localStorage and merge it
                vehicles.forEach(vehicle => {
                    try {
                        const vehicleKey = `vehicle_${vehicle.id}`;
                        const cachedVehicleData = localStorage.getItem(vehicleKey);
                        if (cachedVehicleData) {
                            const cachedVehicle = JSON.parse(cachedVehicleData);
                            // Only use cached data if it's newer than 1 hour
                            const oneHourAgo = Date.now() - (60 * 60 * 1000);
                            if (cachedVehicle.lastUpdated && cachedVehicle.lastUpdated > oneHourAgo) {
                                console.log(`Using cached data for vehicle ${vehicle.id}:`, cachedVehicle);
                                // Merge cached document URLs with server data
                                if (cachedVehicle.rc && !vehicle.rc) {
                                    vehicle.rc = cachedVehicle.rc;
                                    console.log(`Applied cached RC URL to vehicle ${vehicle.id}:`, vehicle.rc);
                                }
                                if (cachedVehicle.d_l && !vehicle.d_l) {
                                    vehicle.d_l = cachedVehicle.d_l;
                                    console.log(`Applied cached DL URL to vehicle ${vehicle.id}:`, vehicle.d_l);
                                }
                            }
                        }
                    } catch (e) {
                        console.error(`Error merging cached data for vehicle ${vehicle.id}:`, e);
                    }
                });
                
                // Sort vehicles by registration date (oldest first)
                vehicles.sort((a, b) => {
                    // Convert registration dates to comparable format
                    const dateA = new Date(a.registrationDate);
                    const dateB = new Date(b.registrationDate);
                    
                    // If dates are valid, sort by date
                    if (!isNaN(dateA) && !isNaN(dateB)) {
                        return dateA - dateB;
                    }
                    // If either date is invalid, keep original order
                    return 0;
                });
                
                // Process location data for all vehicles to prevent duplication
                vehicles.forEach(vehicle => {
                    if (vehicle.location) {
                        const locationParts = vehicle.location.split(',').map(part => part.trim());
                        
                        // Clean up location data to ensure no duplicates
                        const uniqueParts = [];
                        const seenParts = new Set();
                        
                        locationParts.forEach(part => {
                            const lowerPart = part.toLowerCase();
                            if (!seenParts.has(lowerPart)) {
                                seenParts.add(lowerPart);
                                uniqueParts.push(part);
                            }
                        });
                        
                        // Rebuild the location string with unique parts
                        vehicle.location = uniqueParts.join(', ');
                    }
                });
                
                console.log("Vehicles loaded:", vehicles.length);
                
                // Initialize vehicle list with fetched data
                initVehicleList();
                
                // Check if we have a previously selected vehicle ID in localStorage
                const lastSelectedVehicleId = localStorage.getItem('lastSelectedVehicleId');
                
                if (vehicles.length > 0) {
                    if (lastSelectedVehicleId) {
                        // Try to find the previously selected vehicle
                        const previouslySelectedVehicle = vehicles.find(v => v.id == lastSelectedVehicleId);
                        if (previouslySelectedVehicle) {
                            // Select the previously selected vehicle
                            selectVehicle(previouslySelectedVehicle);
                            console.log('Selected previously selected vehicle:', previouslySelectedVehicle.id);
                        } else {
                            // If previously selected vehicle wasn't found, select the first one
                    selectVehicle(vehicles[0]);
                        }
                    } else {
                        // If no previously selected vehicle, select the first one
                        selectVehicle(vehicles[0]);
                    }
                } else {
                    // If no vehicles, hide the details section and document upload section
                    const detailsEl = document.getElementById('vehicleDetails');
                    if (detailsEl) detailsEl.style.display = 'none';
                    const docSection = document.querySelector('.document-upload-section');
                    if (docSection) docSection.style.display = 'none';
                    
                    // Show empty state
                    const emptyState = document.createElement('div');
                    emptyState.className = 'empty-vehicles-state';
                    emptyState.innerHTML = `
                        <div class="empty-icon"><i class="fas fa-truck"></i></div>
                        <h3>No Vehicles Found</h3>
                        <p>You haven't registered any vehicles yet. Click the button below to add your first vehicle.</p>
                        <button class="primary-btn" id="addFirstVehicleBtn">
                            <i class="fas fa-plus"></i> Add Vehicle
                        </button>
                    `;
                    
                    vehicleList.appendChild(emptyState);
                    
                    // Add event listener to the add vehicle button
                    document.getElementById('addFirstVehicleBtn').addEventListener('click', handleAddVehicle);
                }
            } else {
                console.error("Invalid vehicles data format:", data);
                showErrorState('Data Error', 'Could not parse vehicle data correctly. Please try again later.');
            }
        })
        .catch(error => {
            if (isForcedLogoutInProgress) return;
            console.error('Error fetching vehicles:', error);
            showErrorState('Connection Error', 'Could not connect to the server to load your vehicles. Please check your internet connection.');
        });
    }
    
    // Helper function to show error state
    function showErrorState(title, message) {
        // Reset vehicles array
        vehicles = [];
        
        // Clear loading state
        const vehicleList = document.getElementById('vehicleList');
        vehicleList.innerHTML = '';
        
        // Hide details section
        document.getElementById('vehicleDetails').style.display = 'none';
        
        // Show error state
        const errorState = document.createElement('div');
        errorState.className = 'empty-vehicles-state error';
        errorState.innerHTML = `
            <div class="empty-icon"><i class="fas fa-exclamation-circle"></i></div>
            <h3>${title}</h3>
            <p>${message}</p>
            <button class="secondary-btn" id="retryVehiclesBtn">
                <i class="fas fa-redo"></i> Retry
            </button>
        `;
        
        vehicleList.appendChild(errorState);
        
        // Add event listener to the retry button
        document.getElementById('retryVehiclesBtn').addEventListener('click', fetchVehiclesData);
        
        showToast('Failed to load vehicles data', 'error');
    }

    // Initialize everything
    init();

    /* Document Upload Section Functionality */
    function initDocumentUpload() {
        // Guard against multiple init() paths that would bind events twice (causes file picker to open twice).
        if (window.__hpgDocumentUploadInitDone) return;
        window.__hpgDocumentUploadInitDone = true;

        // Get references to document upload elements
        const documentUploadModal = document.getElementById('documentUploadModal');
        const documentPreview = document.getElementById('documentPreview');
        const documentFileInput = document.getElementById('documentFileInput');
        const submitDocumentUpload = document.getElementById('submitDocumentUpload');
        
        // Variables to track document upload state
        let currentDocumentType = null;
        let hasDocumentFile = false;
        
        // Initialize document upload buttons
        const rcUploadBtn = document.getElementById('uploadRcBtn');
        const licenseUploadBtn = document.getElementById('uploadLicenseBtn');
        
        if (rcUploadBtn) {
            rcUploadBtn.addEventListener('click', function() {
                openDocumentModal('rc');
            });
        }
        
        if (licenseUploadBtn) {
            licenseUploadBtn.addEventListener('click', function() {
                openDocumentModal('license');
            });
        }
        
        // Initialize submit button
        if (submitDocumentUpload) {
            submitDocumentUpload.addEventListener('click', uploadDocument);
        }
        
        // Initialize file input
        if (documentFileInput) {
            documentFileInput.addEventListener('change', handleFileSelect);
        }
        
        // Initialize document preview
        if (documentPreview) {
            documentPreview.addEventListener('click', function(e) {
                // prevent immediate re-open if just selected
                if (documentPreview.dataset.fileJustSelected === 'true') {
                e.preventDefault();
                    return false;
                }
                // also block if a file already selected (use replace via double-click only)
                if (documentFileInput && documentFileInput.files && documentFileInput.files.length > 0) {
                    // allow change via explicit click on preview but debounce
                    documentPreview.dataset.fileJustSelected = 'true';
                    setTimeout(()=>{ documentPreview.dataset.fileJustSelected = 'false'; }, 300);
                }
                documentFileInput.click();
            });
            
            // Initialize the flag
            documentPreview.dataset.fileJustSelected = 'false';
        }
        
        function resetDocumentPreview() {
            if (!documentPreview || !documentFileInput || !submitDocumentUpload) return;
            
            // Reset file input
            documentFileInput.value = '';
            
            // Reset preview
            documentPreview.style.backgroundImage = '';
            documentPreview.innerHTML = '<i class="fas fa-file-upload"></i><span>Click to select a file</span>';
            
            // Reset upload button
            submitDocumentUpload.disabled = true;
            submitDocumentUpload.innerHTML = '<i class="fas fa-upload"></i> Upload Document';
            
            // Reset state
            window.hasDocumentFile = false;
            
            // Reset file selection flag
            documentPreview.dataset.fileJustSelected = 'false';
        }
        
        function handleFileSelect(e) {
            const file = e.target.files[0];
            if (file) {
                if (!file.type || !String(file.type).startsWith('image/')) {
                    showToast('Please select an image file only (JPG/PNG/WebP)', 'error');
                    if (documentFileInput) documentFileInput.value = '';
                    submitDocumentUpload.disabled = true;
                    window.hasDocumentFile = false;
                    return;
                }

                if (file.size > 5 * 1024 * 1024) {
                    showToast('File too large. Maximum allowed is 5MB', 'error');
                    if (documentFileInput) documentFileInput.value = '';
                    submitDocumentUpload.disabled = true;
                    window.hasDocumentFile = false;
                    return;
                }

                const reader = new FileReader();
                
                reader.onload = function(e) {
                    // Update preview
                    documentPreview.innerHTML = '';
                    documentPreview.style.backgroundImage = `url(${e.target.result})`;
                    documentPreview.style.backgroundSize = 'cover';
                    documentPreview.style.backgroundPosition = 'center';
                };
                
                reader.readAsDataURL(file);
                
                // Enable upload button
                submitDocumentUpload.disabled = false;
                window.hasDocumentFile = true;
                
                // Prevent the click event from reopening the file dialog immediately
                // We'll use a flag to track if a file was just selected
                documentPreview.dataset.fileJustSelected = 'true';
                
                // Reset the flag after a short delay
                setTimeout(() => {
                    if (documentPreview) {
                        documentPreview.dataset.fileJustSelected = 'false';
                    }
                }, 1000);
            }
        }
        
        async function uploadDocument() {
            if (!window.hasDocumentFile || !window.currentDocumentType || !documentFileInput.files[0]) {
                showToast('Please select a file first', 'error');
                return;
            }

            const originalFile = documentFileInput.files[0];
            if (!originalFile.type || !String(originalFile.type).startsWith('image/')) {
                showToast('Only image files are allowed for RC/DL', 'error');
                return;
            }
            if (originalFile.size > 5 * 1024 * 1024) {
                showToast('File too large. Maximum allowed is 5MB', 'error');
                return;
            }
            
            // Check if a vehicle is selected
            if (!selectedVehicle || !selectedVehicle.id) {
                showToast('No vehicle selected. Please select a vehicle first.', 'error');
                return;
            }
            
            // Show loading state
            submitDocumentUpload.disabled = true;
            submitDocumentUpload.innerHTML = '<i class="fas fa-spinner fa-spin"></i> Uploading...';
            
            // Create form data
                const formData = new FormData();

                // Compress before upload (RC/DL are not displayed publicly; keep reasonably small but readable)
                const compressedFile = await (async () => {
                    try {
                        let bitmap;
                        try { bitmap = await createImageBitmap(originalFile, { imageOrientation: 'from-image' }); } catch (_e) { bitmap = await createImageBitmap(originalFile); }
                        const maxDim = 1800;
                        const scale = Math.min(1, maxDim / bitmap.width, maxDim / bitmap.height);
                        const w = Math.max(1, Math.round(bitmap.width * scale));
                        const h = Math.max(1, Math.round(bitmap.height * scale));
                        const canvas = document.createElement('canvas');
                        canvas.width = w; canvas.height = h;
                        const ctx = canvas.getContext('2d', { alpha: false });
                        ctx.imageSmoothingEnabled = true;
                        ctx.imageSmoothingQuality = 'high';
                        ctx.drawImage(bitmap, 0, 0, w, h);
                        try { bitmap.close(); } catch (_e) {}
                        const toBlob = (type, quality) => new Promise((resolve) => canvas.toBlob(resolve, type, quality));

                        // Use JPEG for document photos (consistent + small)
                        let type = 'image/jpeg';
                        let q = 0.85;
                        let b = await toBlob(type, q);
                        if (!b || !b.size) return originalFile;

                        // Cap around ~900KB to keep storage/bandwidth sane
                        const cap = 900 * 1024;
                        for (let i = 0; i < 8 && b.size > cap; i++) {
                            q = Math.max(0.6, q - 0.06);
                            const nb = await toBlob(type, q);
                            if (!nb || !nb.size) break;
                            b = nb;
                        }

                        if (b.size >= originalFile.size) return originalFile;
                        return new File([b], 'document-compressed.jpg', { type });
                    } catch (e) {
                        console.warn('Document compression failed; uploading original', e);
                        return originalFile;
                    }
                })();

                formData.append('document', compressedFile);
            
            try {
                // Determine the endpoint based on document type
                let endpoint = window.currentDocumentType === 'rc' ? 'rc' : 'dl';
                
                // Include vehicle ID in the API URL path to ensure document is associated with correct vehicle
                const uploadUrl = getApiUrl(`registration/${selectedVehicle.id}/${endpoint}`);
                console.log(`Uploading ${window.currentDocumentType} document for vehicle ${selectedVehicle.id} to: ${uploadUrl}`);
                
                const response = await fetchWithRetry(uploadUrl, {
                    method: 'POST',
                    body: formData,
                    // Do not set Content-Type header when using FormData
                });
                
                if (!response.ok) {
                    throw new Error(`Server responded with status: ${response.status}`);
                }
                
                const data = await response.json();
                
                if (data.success) {
                    // Close modal
                    documentUploadModal.classList.remove('active');
                    
                    // Show success message
                    showToast(`${window.currentDocumentType === 'rc' ? 'RC' : 'Driver License'} uploaded successfully`, 'success');
                    
                    // Update vehicle object with new document URL
                        if (window.currentDocumentType === 'rc') {
                        selectedVehicle.rc = data.url || data.documentUrl || true;
                        } else {
                        selectedVehicle.d_l = data.url || data.documentUrl || true;
                    }
                    
                    // Update document status in UI
                    updateDocumentStatus(currentDocumentType, 'uploaded', data.url || data.documentUrl);
                    
                    // Update the vehicle in the vehicles array
                    const index = vehicles.findIndex(v => v.id === selectedVehicle.id);
                    if (index > -1) {
                        if (window.currentDocumentType === 'rc') {
                            vehicles[index].rc = data.url || data.documentUrl || true;
                        } else {
                            vehicles[index].d_l = data.url || data.documentUrl || true;
                        }
                    }
                    
                    // Update vehicle data in localStorage
                    try {
                        const vehicleKey = `vehicle_${selectedVehicle.id}`;
                        const updatedVehicleData = {
                            ...selectedVehicle,
                            lastUpdated: new Date().getTime()
                        };
                        localStorage.setItem(vehicleKey, JSON.stringify(updatedVehicleData));
                        console.log(`Updated vehicle data in localStorage after document upload:`, updatedVehicleData);
                    } catch (e) {
                        console.error(`Error storing updated vehicle data in localStorage:`, e);
                    }
                    
                    // Update document badge in vehicle number header
                        const vehicleNumberElement = document.getElementById('selectedVehicleNumber');
                        if (vehicleNumberElement) {
                        // Add document status badges if available
                            let badgesHtml = '';
                            
                            // RC badge
                            if (selectedVehicle.rc) {
                                badgesHtml += '<span class="document-badge rc-badge" title="RC Uploaded">RC</span>';
                            }
                            
                            // DL badge
                            if (selectedVehicle.d_l) {
                                badgesHtml += '<span class="document-badge dl-badge" title="Driving License Uploaded">DL</span>';
                            }
                            
                            // For manual carts, don't show the vehicle number
                            const isManualCart = selectedVehicle.type && selectedVehicle.type.toLowerCase().includes('manual') || 
                                              (selectedVehicle.number && selectedVehicle.number.startsWith('MANUAL-CART-'));
                            if (isManualCart) {
                                vehicleNumberElement.innerHTML = `Manual Cart ${badgesHtml}`;
                            } else {
                                vehicleNumberElement.innerHTML = `${escapeHtml(selectedVehicle.number || 'Unknown')} ${badgesHtml}`;
                            }
                    }

                    // Reload the page after a short delay so UI reflects latest doc status everywhere
                    setTimeout(() => {
                        const reloadTimestamp = new Date().getTime();
                        const baseUrl = window.location.href.split('#')[0];
                        const newUrl = baseUrl + (baseUrl.includes('?') ? '&' : '?') + '_t=' + reloadTimestamp;
                        console.log('Reloading page with cache-busting URL after document upload:', newUrl);
                        window.location.href = newUrl;
                    }, 1200);
                } else {
                    throw new Error(data.message || 'Upload failed');
                }
            } catch (error) {
                console.error('Document upload error:', error);
                showToast('Failed to upload document: ' + error.message, 'error');
                
                // Reset button state
                submitDocumentUpload.disabled = false;
                submitDocumentUpload.innerHTML = '<i class="fas fa-upload"></i> Upload Document';
            }
            }
        }
        
    // Function removed - No longer needed as document viewing has been disabled

    // Function to update document status in the UI
    function updateDocumentStatus(documentType, status, documentUrl = null) {
            // Get the document status element for the current vehicle
            const statusElement = document.getElementById(`${documentType}Status`);
            if (!statusElement) return;
            
            const statusBadge = statusElement.querySelector('.status-badge, .status-pill');
            const uploadButton = document.getElementById(documentType === 'rc' ? 'uploadRcBtn' : 'uploadLicenseBtn');
        
            // Get document URL from selectedVehicle if not provided
            if (!documentUrl && selectedVehicle) {
                documentUrl = documentType === 'rc' ? selectedVehicle.rc : selectedVehicle.d_l;
            }
            
            // Store the vehicle ID with the status element to ensure document status is vehicle-specific
            if (selectedVehicle && selectedVehicle.id) {
                statusElement.dataset.vehicleId = selectedVehicle.id;
            }
            
            // Check if this status update is for the currently selected vehicle
            // If the status element has a different vehicle ID, don't update it
            if (statusElement.dataset.vehicleId && 
                selectedVehicle && 
                statusElement.dataset.vehicleId !== selectedVehicle.id.toString()) {
                console.log(`Skipping document status update for ${documentType} - wrong vehicle ID (${statusElement.dataset.vehicleId} vs ${selectedVehicle.id})`);
                return;
            }
            
            if (statusBadge) {
                statusBadge.className = statusBadge.classList.contains('status-pill') ? 'status-pill ' + status : 'status-badge ' + status;
                
                switch (status) {
                    case 'uploaded':
                        statusBadge.innerHTML = '<i class="fas fa-check-circle"></i> Uploaded';
                        statusBadge.className += ' success uploaded';
                        break;
                    case 'verified':
                        statusBadge.innerHTML = '<i class="fas fa-certificate"></i> Verified';
                        break;
                    case 'rejected':
                        statusBadge.innerHTML = '<i class="fas fa-times-circle"></i> Rejected';
                        break;
                    default:
                        statusBadge.innerHTML = '<i class="fas fa-clock"></i> Not Uploaded';
                        statusBadge.className += ' not_uploaded';
                }
            }
            
            // Remove any verification message if it exists
            let verificationMsg = statusElement.querySelector('.verification-message');
            if (verificationMsg) {
                verificationMsg.style.display = 'none';
            }
            
            if (uploadButton) {
                if (status === 'uploaded' || status === 'verified') {
                    // Change button to "Uploaded"
                    uploadButton.innerHTML = '<i class="fas fa-check-circle"></i> Uploaded';
                    uploadButton.classList.add('document-uploaded-btn');
                    uploadButton.disabled = true;
                    
                    // Add document actions container if not exists
                    let actionsContainer = statusElement.querySelector('.document-actions');
                    if (!actionsContainer) {
                        actionsContainer = document.createElement('div');
                        actionsContainer.className = 'document-actions';
                        statusElement.appendChild(actionsContainer);
                    } else {
                        actionsContainer.innerHTML = ''; // Clear existing actions
                    }
                    
                    // We don't need to add the security note here anymore as it's now always visible in the HTML
                    
                    // Add delete button
                    const deleteBtn = document.createElement('button');
                    deleteBtn.className = 'document-action-btn delete-btn';
                    deleteBtn.innerHTML = '<i class="fas fa-trash"></i> Delete';
                    deleteBtn.onclick = () => deleteDocument(documentType);
                    actionsContainer.appendChild(deleteBtn);
                } else {
                    // Reset to upload button
                    uploadButton.innerHTML = uploadButton.classList.contains('doc-upload-btn')
                        ? '<i class="fas fa-cloud-upload-alt"></i> <span>Upload Document</span>'
                        : '<i class="fas fa-upload"></i> Upload Document';
                    uploadButton.classList.remove('document-uploaded-btn');
                    uploadButton.disabled = false;
                    uploadButton.onclick = () => openDocumentModal(documentType);
                    
                    // Remove actions container if exists
                    const actionsContainer = statusElement.querySelector('.document-actions');
                    if (actionsContainer) {
                        statusElement.removeChild(actionsContainer);
                    }
                }
            }

            // Update progress indicator
            updateDocumentProgress();
    }

    // Function to update document upload progress indicator
    function updateDocumentProgress() {
        const docProgressCircle = document.getElementById('docProgressCircle');
        const docProgressText = document.getElementById('docProgressText');
        const docsUploadedCount = document.getElementById('docsUploadedCount');
        
        if (!docProgressCircle || !docProgressText || !docsUploadedCount) return;
        
        // Count uploaded documents for current vehicle
        let uploadedCount = 0;
        const totalDocs = 2; // RC and License
        
        if (selectedVehicle) {
            if (selectedVehicle.rc && selectedVehicle.rc.trim() !== '') uploadedCount++;
            if (selectedVehicle.d_l && selectedVehicle.d_l.trim() !== '') uploadedCount++;
        }
        
        const percentage = Math.round((uploadedCount / totalDocs) * 100);
        
        // Update circular progress
        docProgressCircle.setAttribute('stroke-dasharray', `${percentage}, 100`);
        docProgressText.textContent = `${percentage}%`;
        docsUploadedCount.textContent = uploadedCount;
    }

    
    // Function removed - Document preview functionality has been completely disabled
    
    // Function to delete a document
    async function deleteDocument(documentType) {
        if (!selectedVehicle) {
            showToast('No vehicle selected. Please select a vehicle first.', 'error');
            return;
        }
            
        // Show confirmation dialog
        showConfirmModal(
            'Delete Document',
            `Are you sure you want to delete this ${documentType === 'rc' ? 'Vehicle RC' : 'Driver License'} document?`,
            'Delete',
            'Cancel',
            async () => {
                try {
                    // Show loading state
                    const loadingToast = showToast('Deleting document...', 'info', false);
                    
                    // Call API to delete document with cache-busting
                    const timestamp = new Date().getTime();
                    const deleteUrl = getApiUrl(`registration/${selectedVehicle.id}/${documentType === 'rc' ? 'rc' : 'dl'}/delete`) + 
                        `?_t=${timestamp}`;
                    
                    console.log('Deleting document with URL:', deleteUrl);
                    
                    const response = await fetchWithRetry(
                        deleteUrl,
                        {
                            method: 'DELETE'
                        },
                        3
                    );
                    
                    // Remove loading toast
                    if (loadingToast && loadingToast.remove) {
                        loadingToast.remove();
                    }
                    
                    if (!response.ok) {
                        throw new Error(`Server responded with status: ${response.status}`);
                    }
                    
                    const data = await response.json();
                    
                    if (data.success) {
                        // Update UI and local vehicle object
                        if (documentType === 'rc') {
                            selectedVehicle.rc = null;
                        } else {
                            selectedVehicle.d_l = null;
                        }
                        
                        // Update document status
                        updateDocumentStatus(documentType, 'not_uploaded');
                        
                        // Update vehicle in vehicles array
                        const index = vehicles.findIndex(v => v.id === selectedVehicle.id);
                        if (index > -1) {
                            if (documentType === 'rc') {
                                vehicles[index].rc = null;
                            } else {
                                vehicles[index].d_l = null;
                            }
                        }
                        
                        // Update localStorage to reflect the deletion
                        try {
                            const vehicleKey = `vehicle_${selectedVehicle.id}`;
                            const vehicleData = {
                                ...selectedVehicle,
                                lastUpdated: new Date().getTime()
                            };
                            localStorage.setItem(vehicleKey, JSON.stringify(vehicleData));
                            console.log(`Updated vehicle data in localStorage after document deletion:`, vehicleData);
                        } catch (e) {
                            console.error(`Error updating vehicle data in localStorage:`, e);
                        }
                        
                        // Show success message
                        showToast('Document deleted successfully', 'success');
                        
                        // Update vehicle badges
                        const vehicleNumberElement = document.getElementById('selectedVehicleNumber');
                        if (vehicleNumberElement) {
                            let badgesHtml = '';
                            
                            // RC badge
            if (selectedVehicle.rc) {
                                badgesHtml += '<span class="document-badge rc-badge" title="RC Uploaded">RC</span>';
            }
            
                            // DL badge
            if (selectedVehicle.d_l) {
                                badgesHtml += '<span class="document-badge dl-badge" title="Driving License Uploaded">DL</span>';
                            }
                            
                            // For manual carts, don't show the vehicle number
                            const isManualCart = selectedVehicle.type && selectedVehicle.type.toLowerCase().includes('manual') || 
                                              (selectedVehicle.number && selectedVehicle.number.startsWith('MANUAL-CART-'));
                            
                            if (isManualCart) {
                                vehicleNumberElement.innerHTML = `Manual Cart ${badgesHtml}`;
                            } else {
                                vehicleNumberElement.innerHTML = `${escapeHtml(selectedVehicle.number || 'Unknown')} ${badgesHtml}`;
                            }
                        }
                        
                        // Make a direct API call to verify document deletion
                        fetch(getApiUrl(`vehicles/${selectedVehicle.id}/documents?_t=${new Date().getTime()}`), {
                            method: 'GET',
                            headers: {
                                'Cache-Control': 'no-cache, no-store, must-revalidate',
                                'Pragma': 'no-cache',
                                'Expires': '0'
                            }
                        })
                        .then(response => response.json())
                        .then(freshData => {
                            console.log("Verified document deletion with fresh data:", freshData);
                        })
                        .catch(error => {
                            console.warn("Error verifying document deletion:", error);
                        })
                        .finally(() => {
                            // Reload the page after a short delay
                            setTimeout(() => {
                                // Use a full page reload with timestamp to prevent caching issues
                                const reloadTimestamp = new Date().getTime();
                                const baseUrl = window.location.href.split('#')[0];
                                const newUrl = baseUrl + (baseUrl.includes('?') ? '&' : '?') + '_t=' + reloadTimestamp;
                                console.log('Reloading page with cache-busting URL after document deletion:', newUrl);
                                window.location.href = newUrl;
                            }, 1500); // 1.5 seconds delay so user can see success message
                        });
                    } else {
                        throw new Error(data.message || 'Failed to delete document');
                    }
                } catch (error) {
                    console.error('Error deleting document:', error);
                    showToast('Failed to delete document: ' + error.message, 'error');
                }
            }
        );
    }
    
    // Function to open document modal - moved outside to be accessible
    function openDocumentModal(documentType) {
        const modal = document.getElementById('documentUploadModal');
        if (!modal) { console.warn('Document modal not found'); return; }
        window.currentDocumentType = documentType;
        resetDocumentPreview();
        const header = document.getElementById('documentUploadTitle');
        if (header) header.textContent = documentType === 'rc' ? 'Upload Vehicle RC' : 'Upload Driver License';
        modal.classList.add('active');
        const closeBtn = document.getElementById('closeDocumentModal');
        if (closeBtn) closeBtn.onclick = () => { modal.classList.remove('active'); resetDocumentPreview(); };
        const cancelBtn = document.getElementById('cancelDocumentUpload');
        if (cancelBtn) cancelBtn.onclick = () => { modal.classList.remove('active'); resetDocumentPreview(); };
    }
    
    // Function to reset document preview - moved outside to be accessible
    function resetDocumentPreview() {
        const documentPreview = document.getElementById('documentPreview');
        const documentFileInput = document.getElementById('documentFileInput');
        const submitDocumentUpload = document.getElementById('submitDocumentUpload');
        
        if (!documentPreview || !documentFileInput || !submitDocumentUpload) return;
        
        // Reset file input
        documentFileInput.value = '';
        
        // Reset preview
        documentPreview.style.backgroundImage = '';
        documentPreview.innerHTML = '<i class="fas fa-file-upload"></i><span>Click to select a file</span>';
        
        // Reset upload button
        submitDocumentUpload.disabled = true;
        submitDocumentUpload.innerHTML = '<i class="fas fa-upload"></i> Upload Document';
        
        // Reset state
        window.hasDocumentFile = false;
        
        // Reset file selection flag
        documentPreview.dataset.fileJustSelected = 'false';
    }

    // Add document upload initialization to the main init function
    function init() {
        checkAuth();
        initUserInfo();
        initVehicleList();
        initProfilePhoto();
        initTabs();
        initEventListeners();
        setupPhoneValidation();
        checkAndUploadTempProfilePhoto();
        initDocumentUpload(); // Initialize document upload functionality
    }

    // Removed drag and drop functionality

    // Function to initialize document status for a vehicle
    function initializeDocumentStatus(vehicle) {
        if (!vehicle) return;
        
        console.log("Initializing document status for vehicle:", vehicle.id);
        
        // First, reset document status elements to ensure they're cleared for the new vehicle
        const rcStatusElement = document.getElementById('rcStatus');
        const licenseStatusElement = document.getElementById('licenseStatus');
        
        if (rcStatusElement) {
            // Set or update the vehicle ID for this status element
            rcStatusElement.dataset.vehicleId = vehicle.id;
            
            // Reset to default state
            const rcStatusBadge = rcStatusElement.querySelector('.status-badge, .status-pill');
            const rcUploadButton = document.getElementById('uploadRcBtn');
            
            if (rcStatusBadge) {
                if (rcStatusBadge.classList.contains('status-pill')) {
                    rcStatusBadge.className = 'status-pill not_uploaded';
                    rcStatusBadge.innerHTML = '<i class="fas fa-clock"></i> Not Uploaded';
                } else {
                    rcStatusBadge.className = 'status-badge not_uploaded';
                    rcStatusBadge.textContent = 'Not Uploaded';
                }
            }
            
            if (rcUploadButton) {
                rcUploadButton.innerHTML = rcUploadButton.classList.contains('doc-upload-btn')
                    ? '<i class="fas fa-cloud-upload-alt"></i> <span>Upload Document</span>'
                    : '<i class="fas fa-upload"></i> Upload Document';
                rcUploadButton.classList.remove('document-uploaded-btn');
                rcUploadButton.disabled = false;
                rcUploadButton.onclick = () => openDocumentModal('rc');
            }
            
            // Remove any actions container
            const rcActionsContainer = rcStatusElement.querySelector('.document-actions');
            if (rcActionsContainer) {
                rcStatusElement.removeChild(rcActionsContainer);
            }
        }
        
        if (licenseStatusElement) {
            // Set or update the vehicle ID for this status element
            licenseStatusElement.dataset.vehicleId = vehicle.id;
            
            // Reset to default state
            const licenseStatusBadge = licenseStatusElement.querySelector('.status-badge, .status-pill');
            const licenseUploadButton = document.getElementById('uploadLicenseBtn');
            
            if (licenseStatusBadge) {
                if (licenseStatusBadge.classList.contains('status-pill')) {
                    licenseStatusBadge.className = 'status-pill not_uploaded';
                    licenseStatusBadge.innerHTML = '<i class="fas fa-clock"></i> Not Uploaded';
                } else {
                    licenseStatusBadge.className = 'status-badge not_uploaded';
                    licenseStatusBadge.textContent = 'Not Uploaded';
                }
            }
            
            if (licenseUploadButton) {
                licenseUploadButton.innerHTML = licenseUploadButton.classList.contains('doc-upload-btn')
                    ? '<i class="fas fa-cloud-upload-alt"></i> <span>Upload Document</span>'
                    : '<i class="fas fa-upload"></i> Upload Document';
                licenseUploadButton.classList.remove('document-uploaded-btn');
                licenseUploadButton.disabled = false;
                licenseUploadButton.onclick = () => openDocumentModal('license');
            }
            
            // Remove any actions container
            const licenseActionsContainer = licenseStatusElement.querySelector('.document-actions');
            if (licenseActionsContainer) {
                licenseStatusElement.removeChild(licenseActionsContainer);
            }
        }
        
        // Get document URLs from vehicle data
        const rcUrl = vehicle.rc;
        const dlUrl = vehicle.d_l;
        
        // Initialize RC document status - if URL exists, consider it uploaded
        if (rcUrl) {
            console.log("RC document URL exists for vehicle " + vehicle.id + ":", rcUrl);
            updateDocumentStatus('rc', 'uploaded', rcUrl);
        } else {
            console.log("No RC document URL for vehicle " + vehicle.id);
            updateDocumentStatus('rc', 'not_uploaded');
        }
        
        // Initialize DL document status - if URL exists, consider it uploaded
        if (dlUrl) {
            console.log("DL document URL exists for vehicle " + vehicle.id + ":", dlUrl);
            updateDocumentStatus('license', 'uploaded', dlUrl);
        } else {
            console.log("No DL document URL for vehicle " + vehicle.id);
            updateDocumentStatus('license', 'not_uploaded');
        }
        
        // Make a direct API call to get the latest document status
        // This ensures we have the most up-to-date information
        console.log(`Fetching latest document status for vehicle ${vehicle.id}...`);
        fetch(getApiUrl(`vehicles/${vehicle.id}/documents?_t=${new Date().getTime()}`), {
            method: 'GET',
            headers: {
                'Cache-Control': 'no-cache, no-store, must-revalidate',
                'Pragma': 'no-cache',
                'Expires': '0'
            }
        })
        .then(response => {
            if (!response.ok) {
                // Try fallback endpoint
                return fetch(getApiUrl(`registration/${vehicle.id}/documents?_t=${new Date().getTime()}`), {
                    headers: {
                        'Cache-Control': 'no-cache, no-store, must-revalidate',
                        'Pragma': 'no-cache',
                        'Expires': '0'
                    }
                })
                .then(fallbackResponse => {
                    if (!fallbackResponse.ok) {
                        throw new Error(`Failed to fetch document status: ${response.status}, fallback: ${fallbackResponse.status}`);
                    }
                    return fallbackResponse.json();
                });
            }
            return response.json();
        })
        .then(data => {
            console.log(`Received document status for vehicle ${vehicle.id}:`, data);
            
            // Check if the currently selected vehicle is still the same one
            // If not, don't update the UI (user might have switched vehicles)
            if (selectedVehicle && selectedVehicle.id !== vehicle.id) {
                console.log(`Vehicle selection changed during API call. Was ${vehicle.id}, now ${selectedVehicle.id}`);
                return;
            }
            
            if (data && data.documents) {
                // Get the index of the vehicle in the array
                const index = vehicles.findIndex(v => v.id === vehicle.id);
                
                // Update RC status
                if (data.documents.rc && data.documents.rc.url) {
                    console.log(`Found RC URL in API response for vehicle ${vehicle.id}:`, data.documents.rc.url);
                    vehicle.rc = data.documents.rc.url;
                    updateDocumentStatus('rc', 'uploaded', data.documents.rc.url);
                    
                    // Update in vehicles array
                    if (index > -1) {
                        vehicles[index].rc = data.documents.rc.url;
                    }
                } else {
                    // If no RC document found in API response, clear any cached RC URL
                    console.log(`No RC document found in API response for vehicle ${vehicle.id}, clearing any cached data`);
                    vehicle.rc = null;
                    updateDocumentStatus('rc', 'not_uploaded');
                    
                    // Update in vehicles array
                    if (index > -1) {
                        vehicles[index].rc = null;
                    }
                }
                
                // Update DL status
                if (data.documents.dl && data.documents.dl.url) {
                    console.log(`Found DL URL in API response for vehicle ${vehicle.id}:`, data.documents.dl.url);
                    vehicle.d_l = data.documents.dl.url;
                    updateDocumentStatus('license', 'uploaded', data.documents.dl.url);
                    
                    // Update in vehicles array
                    if (index > -1) {
                        vehicles[index].d_l = data.documents.dl.url;
                    }
                } else {
                    // If no DL document found in API response, clear any cached DL URL
                    console.log(`No DL document found in API response for vehicle ${vehicle.id}, clearing any cached data`);
                    vehicle.d_l = null;
                    updateDocumentStatus('license', 'not_uploaded');
                    
                    // Update in vehicles array
                    if (index > -1) {
                        vehicles[index].d_l = null;
                    }
                }
                
                // Always store the latest vehicle data in localStorage, ensuring deleted documents stay deleted
                try {
                    const vehicleKey = `vehicle_${vehicle.id}`;
                    const updatedVehicleData = {
                        ...vehicle,
                        lastUpdated: new Date().getTime()
                    };
                    localStorage.setItem(vehicleKey, JSON.stringify(updatedVehicleData));
                    console.log(`Updated vehicle data in localStorage after document check:`, updatedVehicleData);
                    
                    // Clear any document-specific cached data
                    if (!vehicle.rc) {
                        localStorage.removeItem(`document_rc_${vehicle.id}`);
                        console.log(`Cleared RC document cache for vehicle ${vehicle.id}`);
                    }
                    if (!vehicle.d_l) {
                        localStorage.removeItem(`document_license_${vehicle.id}`);
                        console.log(`Cleared DL document cache for vehicle ${vehicle.id}`);
                    }
                } catch (e) {
                    console.error(`Error storing updated vehicle data in localStorage:`, e);
                }
            }
        })
        .catch(error => {
            console.warn(`Error fetching document status for vehicle ${vehicle.id}:`, error);
        });
    }
    
    function selectVehicle(vehicle) {
        if (!vehicle) {
            console.error("Cannot select vehicle: No vehicle data provided");
            return;
        }
        
        console.log("Selecting vehicle:", vehicle);
        
        // Set the global selected vehicle
        selectedVehicle = vehicle;
        
        // Store the selected vehicle ID in localStorage for persistence
        storeSelectedVehicleId(vehicle.id);
        
        // Update selected card in UI
        document.querySelectorAll('.vehicle-card').forEach(card => {
            card.classList.remove('selected');
            if (card.getAttribute('data-vehicle') === vehicle.id) {
                card.classList.add('selected');
            }
        });
        
        // Update vehicle details section
        const detailsSection = document.getElementById('vehicleDetails');
        if (!detailsSection) {
            console.error("Vehicle details section not found");
            return;
        }
        
        detailsSection.style.display = 'block';
        
        // Check if this is a manual cart
        const isManualCart = vehicle.type && vehicle.type.toLowerCase().includes('manual') || 
                             (vehicle.number && vehicle.number.startsWith('MANUAL-CART-'));
        
        // Show/hide document upload section based on vehicle type
        const documentUploadSection = document.querySelector('.document-upload-section');
        if (documentUploadSection) {
            documentUploadSection.style.display = isManualCart ? 'none' : 'block';
        }
        
        // Update vehicle number in header
        const vehicleNumberElement = document.getElementById('selectedVehicleNumber');
        if (vehicleNumberElement) {
            // Add document status badges if available
            let badgesHtml = '';
            
            // RC badge
            if (vehicle.rc) {
                badgesHtml += '<span class="document-badge rc-badge" title="RC Uploaded">RC</span>';
            }
            
            // DL badge
            if (vehicle.d_l) {
                badgesHtml += '<span class="document-badge dl-badge" title="Driving License Uploaded">DL</span>';
            }
            
            // For manual carts, don't show the vehicle number
            if (isManualCart) {
                vehicleNumberElement.innerHTML = `Manual Cart ${badgesHtml}`;
            } else {
                vehicleNumberElement.innerHTML = `${escapeHtml(vehicle.number || 'Unknown')} ${badgesHtml}`;
            }
        }
        
        // Initialize document status
        initializeDocumentStatus(vehicle);
        
        // Update details content
        const detailsContent = detailsSection.querySelector('.vehicle-details-content');
        if (!detailsContent) {
            console.error("Vehicle details content section not found");
            return;
        }
        
        // Handle missing data with defaults
        const vehicleOwner = vehicle.owner || userData.name || 'You';
        
        // Format registration date properly
        let registrationDate = 'Not available';
        if (vehicle.registrationDate) {
            try {
                // Parse the date (handles both yyyy-mm-dd format and other formats)
                const dateObj = new Date(vehicle.registrationDate);
                if (!isNaN(dateObj.getTime())) {
                    // Format date as DD MMM YYYY (e.g., 15 Jan 2024)
                    registrationDate = dateObj.toLocaleDateString('en-IN', {
                        day: 'numeric',
                        month: 'short',
                        year: 'numeric'
                    });
                }
            } catch (e) {
                console.error("Error formatting registration date:", e);
            }
        }
        
        const contactNumber = vehicle.contact || userData.contact || 'Not provided';
        const whatsappNumber = vehicle.whatsapp ? vehicle.whatsapp : 'WhatsApp number not provided';
        const alternateContactNumber = vehicle.alternateContact || 'Not provided';
        
        // Process location to prevent duplication
        let location = vehicle.location || 'Not specified';
        if (location !== 'Not specified') {
            const locationParts = location.split(',').map(part => part.trim());
            
            // Check for duplicate state entries
            if (locationParts.length > 2) {
                // Extract unique location parts to prevent duplications
                const uniqueParts = [];
                const seenParts = new Set();
                
                locationParts.forEach(part => {
                    const lowerPart = part.toLowerCase();
                    if (!seenParts.has(lowerPart)) {
                        seenParts.add(lowerPart);
                        uniqueParts.push(part);
                    }
                });
                
                // Rebuild the location string with unique parts
                location = uniqueParts.join(', ');
            }
        }
        
        const pincode = vehicle.pincode || 'Not specified';
        
        // Create HTML for vehicle details with conditional registration number
        let detailsHTML = `
            <div>
                <div class="detail-group">
                    <div class="detail-label">Vehicle Type</div>
                    <div class="detail-value">${escapeHtml(vehicle.type || 'Unknown')}</div>
                </div>`;
                
        // Only show vehicle number for non-manual carts
        if (!isManualCart) {
            detailsHTML += `
                <div class="detail-group">
                    <div class="detail-label">Vehicle Number</div>
                    <div class="detail-value">${escapeHtml(vehicle.number || 'Unknown')}</div>
                </div>`;
        }
        
        // Continue with the rest of the details
        detailsHTML += `
                <div class="detail-group">
                    <div class="detail-label">Owner Name</div>
                    <div class="detail-value">${escapeHtml(vehicleOwner)}</div>
                </div>
                <div class="detail-group">
                    <div class="detail-label">Registration Date</div>
                    <div class="detail-value">${escapeHtml(registrationDate)}</div>
                </div>
            </div>
            <div>
                <div class="detail-group">
                    <div class="detail-label">Contact Number</div>
                    <div class="detail-value">${escapeHtml(contactNumber)}</div>
                </div>
                <div class="detail-group">
                    <div class="detail-label">WhatsApp Number</div>
                    <div class="detail-value">${escapeHtml(whatsappNumber)}</div>
                </div>
                <div class="detail-group">
                    <div class="detail-label">Alternate Contact</div>
                    <div class="detail-value">${escapeHtml(alternateContactNumber)}</div>
                </div>
                <div class="detail-group">
                    <div class="detail-label">Location</div>
                    <div class="detail-value">${escapeHtml(location)}</div>
                </div>
                <div class="detail-group">
                    <div class="detail-label">Pincode</div>
                    <div class="detail-value">${escapeHtml(pincode)}</div>
                </div>
            </div>
        `;
        
        detailsContent.innerHTML = detailsHTML;
        
        // Add service highlights section to view mode
        const highlightsData = vehicle.highlights || {};
        const activeHighlights = [];
        
        // Collect all active highlights
        if (highlightsData.highlight1) activeHighlights.push(highlightsData.highlight1);
        if (highlightsData.highlight2) activeHighlights.push(highlightsData.highlight2);
        if (highlightsData.highlight3) activeHighlights.push(highlightsData.highlight3);
        if (highlightsData.highlight4) activeHighlights.push(highlightsData.highlight4);
        if (highlightsData.highlight5) activeHighlights.push(highlightsData.highlight5);
        
        // Update document status in the UI based on vehicle data
        if (vehicle.rc) {
            updateDocumentStatus('rc', 'uploaded', vehicle.rc);
        } else {
            updateDocumentStatus('rc', 'not_uploaded');
        }
        
        if (vehicle.d_l) {
            updateDocumentStatus('license', 'uploaded', vehicle.d_l);
        } else {
            updateDocumentStatus('license', 'not_uploaded');
        }
        
        // Create service highlights HTML
        let serviceHighlightsHTML = '';
        if (activeHighlights.length > 0) {
            serviceHighlightsHTML = `
                <div class="full-width service-highlights-view">
                    <div class="service-highlights-header">
                        <div class="service-highlights-title">Service Highlights</div>
                    </div>
                    <div class="service-highlights-list">
                        ${activeHighlights.map(highlight => `
                            <div class="highlight-tag">
                                <span>${escapeHtml(highlight)}</span>
                            </div>
                        `).join('')}
                    </div>
                </div>
            `;
        } else {
            serviceHighlightsHTML = `
                <div class="full-width service-highlights-view">
                    <div class="service-highlights-header">
                        <div class="service-highlights-title">Service Highlights</div>
                    </div>
                    <div class="service-highlights-empty">
                        No service highlights selected. Click "Edit Details" to add highlights.
                    </div>
                </div>
            `;
        }
        
        detailsContent.insertAdjacentHTML('beforeend', serviceHighlightsHTML);
        
        // Update photos grid
        const photosGrid = detailsSection.querySelector('.photos-grid');
        if (!photosGrid) {
            console.error("Photos grid not found");
            return;
        }
        
        // Use default images if photos are not available
        const defaultImage = "attached_assets/images/default-vehicle.png";
        
        // Get photos from vehicle data
        let photos = vehicle.photos || {};
        
        // Avoid logging PII / user-uploaded URLs to the console
        
        // For mock data, create default photo URLs
        if (USE_MOCK_DATA && (!photos || Object.keys(photos).length === 0)) {
            // Create mock photo URLs based on vehicle type
            const typeForImage = vehicle.type.toLowerCase().includes('tata ace') ? 
                'Tata Ace (Chhota Hathi)' : 
                (vehicle.type.toLowerCase().includes('eicher') ? 
                    'mini truck (Eicher Canter)' : 'bolero pickup-truck');
            
            photos = {
                front: `attached_assets/images/${typeForImage}.png`,
                side: `attached_assets/images/${typeForImage}.png`,
                back: `attached_assets/images/${typeForImage}.png`,
                loading: `attached_assets/images/${typeForImage}.png`
            };
        }
        
        // Fix any undefined photo values
        const photoTypes = ['front', 'side', 'back', 'loading'];
        photoTypes.forEach(type => {
            if (!photos[type]) {
                photos[type] = defaultImage;
            }
        });
        
        photosGrid.textContent = '';
        const photoDefs = [
            { key: 'front', label: 'Front View', alt: 'Front View' },
            { key: 'side', label: 'Side View', alt: 'Side View' },
            { key: 'back', label: 'Back View', alt: 'Back View' },
            { key: 'loading', label: 'Loading Area', alt: 'Loading Area' },
        ];
        photoDefs.forEach(({ key, label, alt }) => {
            const wrap = document.createElement('div');
            wrap.className = 'photo-item';

            const img = document.createElement('img');
            img.alt = alt;
            img.src = photos[key] || defaultImage;
            img.onerror = function () { this.src = defaultImage; };

            const lab = document.createElement('div');
            lab.className = 'photo-label';
            lab.textContent = label;

            wrap.appendChild(img);
            wrap.appendChild(lab);
            photosGrid.appendChild(wrap);
        });
    }
});

// Admin Panel Functions
// Check if user is admin
function checkAdminStatus() {
    // For demo purposes, we'll check if the user ID is 1 (or any specific ID you want to designate as admin)
    // In a real application, you would check a role or admin flag in the user data
    if (userData && userData.id === 1) {
        console.log('User is admin, showing admin panel');
        document.querySelectorAll('.admin-only').forEach(el => {
            el.style.display = 'block';
        });
    }
}

// Search for vehicles in the admin panel
function adminSearchVehicles() {
    const searchInput = document.getElementById('adminSearchInput').value.trim().toLowerCase();
    if (!searchInput) {
        showToast('Please enter a search term', 'warning');
        return;
    }

    // Show loading state
    const adminResultsBody = document.getElementById('adminResultsBody');
    adminResultsBody.innerHTML = '<tr><td colspan="5" class="text-center">Searching...</td></tr>';

    // Make API call to search for vehicles
    fetch(`${API_BASE_URL}registration/search?term=${encodeURIComponent(searchInput)}`)
        .then(response => response.json())
        .then(data => {
            console.log('Admin search results:', data);
            displayAdminSearchResults(data);
        })
        .catch(error => {
            console.error('Error searching vehicles:', error);
            adminResultsBody.innerHTML = '<tr><td colspan="5" class="text-center">Error searching vehicles. Please try again.</td></tr>';
        });
}

// Display admin search results
function displayAdminSearchResults(vehicles) {
    const adminResultsBody = document.getElementById('adminResultsBody');
    adminResultsBody.innerHTML = '';

    if (!vehicles || vehicles.length === 0) {
        adminResultsBody.innerHTML = '<tr><td colspan="5" class="text-center">No vehicles found</td></tr>';
        return;
    }

    vehicles.forEach(vehicle => {
        const row = document.createElement('tr');
        
        // Create vehicle ID cell
        const idCell = document.createElement('td');
        idCell.textContent = vehicle.id || 'N/A';
        row.appendChild(idCell);
        
        // Create owner name cell
        const nameCell = document.createElement('td');
        nameCell.textContent = vehicle.fullName || 'N/A';
        row.appendChild(nameCell);
        
        // Create vehicle type cell
        const typeCell = document.createElement('td');
        typeCell.textContent = vehicle.vehicleType || 'N/A';
        row.appendChild(typeCell);
        
        // Create membership cell
        const membershipCell = document.createElement('td');
        const membershipStatus = vehicle.membership || 'Standard';
        membershipCell.textContent = membershipStatus;
        membershipCell.style.fontWeight = 'bold';
        membershipCell.style.color = membershipStatus === 'Premium' ? '#FFD700' : '#0d6efd';
        row.appendChild(membershipCell);
        
        // Create actions cell
        const actionsCell = document.createElement('td');
        
        // Create toggle button based on current membership
        const toggleBtn = document.createElement('button');
        toggleBtn.className = 'admin-action-btn ' + 
            (membershipStatus === 'Premium' ? 'admin-standard-btn' : 'admin-premium-btn');
        toggleBtn.textContent = membershipStatus === 'Premium' ? 'Set Standard' : 'Set Premium';
        toggleBtn.onclick = () => toggleMembership(vehicle.id, membershipStatus);
        
        actionsCell.appendChild(toggleBtn);
        row.appendChild(actionsCell);
        
        adminResultsBody.appendChild(row);
    });
}

// Toggle membership status
function toggleMembership(vehicleId, currentStatus) {
    if (!vehicleId) {
        showToast('Invalid vehicle ID', 'error');
        return;
    }
    
    const newStatus = currentStatus === 'Premium' ? 'Standard' : 'Premium';
    
    // Show confirmation
    if (!confirm(`Are you sure you want to change the membership status to ${newStatus}?`)) {
        return;
    }
    
    // Make API call to update membership
    fetch(`${API_BASE_URL}registration/${vehicleId}/membership`, {
        method: 'PUT',
        headers: {
            'Content-Type': 'application/json',
        },
        body: JSON.stringify({ membership: newStatus })
    })
    .then(response => {
        if (!response.ok) {
            throw new Error('Failed to update membership status');
        }
        return response.json();
    })
    .then(data => {
        console.log('Membership updated:', data);
        showToast(`Membership status updated to ${newStatus}`, 'success');
        
        // Refresh the search results
        adminSearchVehicles();
    })
    .catch(error => {
        console.error('Error updating membership:', error);
        showToast('Failed to update membership status. Please try again.', 'error');
    });
}

// Document ready event listeners for admin panel
document.addEventListener('DOMContentLoaded', function() {
    // Check if admin panel tab button exists
    const adminPanelTabBtn = document.getElementById('adminPanelTabBtn');
    if (adminPanelTabBtn) {
        // Admin panel tab click handler
        adminPanelTabBtn.addEventListener('click', function(e) {
            e.preventDefault();
            
            // Hide all tabs
            document.querySelectorAll('.tab-content').forEach(tab => {
                tab.style.display = 'none';
            });
            
            // Remove active class from all nav items
            document.querySelectorAll('.dashboard-nav a').forEach(navItem => {
                navItem.classList.remove('active');
            });
            
            // Show admin panel tab and mark nav item as active
            document.getElementById('adminPanelTab').style.display = 'block';
            this.classList.add('active');
        });
    }
    
    // Admin search button click handler
    const adminSearchBtn = document.getElementById('adminSearchBtn');
    if (adminSearchBtn) {
        adminSearchBtn.addEventListener('click', adminSearchVehicles);
    }
    
    // Admin search input enter key handler
    const adminSearchInput = document.getElementById('adminSearchInput');
    if (adminSearchInput) {
        adminSearchInput.addEventListener('keypress', function(e) {
            if (e.key === 'Enter') {
                adminSearchVehicles();
            }
        });
    }
    
    // Check if user is admin
    setTimeout(() => {
        checkAdminStatus();
    }, 1000); // Delay to ensure user data is loaded
}); 