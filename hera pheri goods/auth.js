document.addEventListener('DOMContentLoaded', function () {
    const API_BASE_URL = (window.API_BASE_URL ? `${window.API_BASE_URL}/auth` : "http://localhost:8080/auth");
    console.log("Auth module loaded, API endpoint:", API_BASE_URL);

    const loginForm = document.querySelector('.login-form');
    const signupForm = document.querySelector('.signup-form');
    const showSignupLink = document.getElementById('showSignup');
    const showLoginLink = document.getElementById('showLogin');
    const loginBtn = document.getElementById('loginBtn');
    const signupBtn = document.getElementById('signupBtn');
    
    // Debug elements
    const loginDebugInfo = document.getElementById('loginDebugInfo');
    const signupDebugInfo = document.getElementById('signupDebugInfo');
    
    // Timer variable
    let otpTimer = null;
    let timeRemaining = 0;
    let otpExpired = false;
    let currentContactNumber = '';
    let wrongPasswordCount = 0;

    // Two-step login state: 1) verify mobile exists 2) show password
    const loginFormEl = document.getElementById('loginForm');
    const loginPhoneEl = document.getElementById('loginContactNumber');
    const loginPasswordEl = document.getElementById('loginPassword');
    const loginPasswordGroupEl = document.getElementById('loginPasswordGroup');
    const forgotPasswordBlockEl = document.getElementById('forgotPasswordBlock');
    let loginStage = 'phone'; // 'phone' | 'password'
    let verifiedContactNumber = '';

    function setLoginStage(stage) {
        loginStage = stage;
        if (loginStage === 'phone') {
            verifiedContactNumber = '';
            wrongPasswordCount = 0;
            if (loginPasswordGroupEl) loginPasswordGroupEl.style.display = 'none';
            if (forgotPasswordBlockEl) forgotPasswordBlockEl.style.display = 'none';
            if (loginPasswordEl) loginPasswordEl.value = '';
            if (loginBtn) loginBtn.textContent = 'Continue';
            return;
        }

        if (loginPasswordGroupEl) loginPasswordGroupEl.style.display = 'block';
        if (forgotPasswordBlockEl) forgotPasswordBlockEl.style.display = 'block';
        if (loginBtn) loginBtn.textContent = 'Login';
    }

    // Initialize login form to phone-only
    if (loginFormEl && loginPhoneEl) {
        setLoginStage('phone');
        loginPhoneEl.addEventListener('input', function () {
            const normalized = (loginPhoneEl.value || '').trim();
            if (!verifiedContactNumber) return;
            if (normalized !== verifiedContactNumber) {
                setLoginStage('phone');
            }
        });
    }

    // 🔹 Switch between login and signup forms
    showSignupLink.addEventListener('click', (e) => {
        e.preventDefault();
        loginForm.style.display = 'none';
        signupForm.style.display = 'block';
    });

    showLoginLink.addEventListener('click', (e) => {
        e.preventDefault();
        signupForm.style.display = 'none';
        loginForm.style.display = 'block';
    });

    // 🔹 Login Form Submission (two-step: verify mobile -> password)
    document.getElementById('loginForm').addEventListener('submit', async function (e) {
        e.preventDefault();
        // Important: this form contains non-submit buttons (password eye toggle).
        // Always target the real submit button to avoid replacing the eye with text.
        const button = this.querySelector('button[type="submit"]') || document.getElementById('loginBtn');
        const mobileInput = document.getElementById('loginContactNumber') || this.querySelector('input[type="tel"]');
        // Use ID so it works even when toggled to type="text"
        const passwordInput = document.getElementById('loginPassword');

        if (!validateMobile(mobileInput.value)) {
            showToast('Please enter a valid 10-digit mobile number', 'error');
            return;
        }
        const contactNumber = (mobileInput.value || '').trim();

        // Step 1: verify number exists (reduce scams / stop random password attempts)
        if (loginStage === 'phone') {
            button.disabled = true;
            button.innerHTML = '<i class="fas fa-spinner fa-spin" aria-hidden="true"></i> Checking...';
            try {
                const res = await fetch(`${API_BASE_URL}/check-user`, {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ contactNumber })
                });
                const data = await handleApiResponse(res);
                const exists = !!(data && (data.exists === true || data.exists === 'true'));
                if (!exists) {
                    showToast('Number not registered. Please sign up first.', 'error');
                    setLoginStage('phone');
                    return;
                }
                verifiedContactNumber = contactNumber;
                setLoginStage('password');
                showToast('Number verified. Please enter password.', 'success');
                if (passwordInput) passwordInput.focus();
                return;
            } catch (err) {
                showToast((err && err.message) ? err.message : 'Failed to verify number. Please try again.', 'error');
                return;
            } finally {
                button.disabled = false;
                button.textContent = (loginStage === 'phone') ? 'Continue' : 'Login';
            }
        }

        // Step 2: password login
        if (verifiedContactNumber && contactNumber !== verifiedContactNumber) {
            setLoginStage('phone');
            showToast('Number changed. Please verify again.', 'error');
            return;
        }

        const pwTrimmed = passwordInput ? passwordInput.value.trim() : '';
        const hasPassword = !!pwTrimmed;
        if (!hasPassword) {
            showToast('Please enter your password', 'error');
            return;
        }

        button.disabled = true;
        button.innerHTML = '<i class="fas fa-spinner fa-spin" aria-hidden="true"></i> Logging in...';
        try {
            const payload = { contactNumber: contactNumber };
            if (hasPassword) payload.password = pwTrimmed;
            const res = await fetch(`${API_BASE_URL}/login`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(payload),
            });
            const data = await handleApiResponse(res);
            if (data && data.message && data.message.includes('Login successful')) {
                localStorage.setItem('isLoggedIn', 'true');
                localStorage.setItem('userPhone', contactNumber);
                if (data.token) {
                    if (window.AuthToken && typeof window.AuthToken.set === 'function') {
                        window.AuthToken.set(data.token);
                    } else {
                        // Fallback (should be rare if env.js loaded)
                        sessionStorage.setItem('authToken', data.token);
                        try { localStorage.removeItem('authToken'); } catch (_e) {}
                    }
                }
                showToast('Login successful!', 'success');
                setTimeout(() => {
                    const params = new URLSearchParams(window.location.search);
                    const redir = params.get('redirect');
                    let target = 'index';
                    if (redir === 'register') target = 'register';
                    if (redir === 'driver-dashboard') target = 'driver-dashboard';
                    window.location.href = target;
                }, 800);
                return;
            }
            showToast(data.message || 'Please use password or OTP login', 'error');
        } catch (err) {
            const msg = (err && err.message) ? err.message : 'Login failed';
            // Wrong password handling
            if (msg.toLowerCase().includes('invalid password')) {
                wrongPasswordCount += 1;
                if (wrongPasswordCount >= 3) {
                    alert("You've entered an incorrect password multiple times.\nFor your safety, we've redirected you to the homepage. Please try again later or contact support if you're facing issues.\n\n🔒 Your security is our priority");
                    window.location.href = 'index';
                    return;
                }
            }
            // Captcha error on password-less attempts
            if (msg.toLowerCase().includes('captcha verification failed')) {
                showToast('Please enter password to login, or use Forgot Password (OTP).', 'error');
            } else {
                showToast(msg, 'error');
            }
        } finally {
            button.disabled = false;
            // Restore the default label (and avoid leaving spinner HTML behind)
            button.textContent = (loginStage === 'phone') ? 'Continue' : 'Login';
        }
    });

    // 🔹 Signup Form Submission (direct signup, no OTP) with loading guard
    document.getElementById('signupForm').addEventListener('submit', async function (e) {
        e.preventDefault();
        // Important: this form contains non-submit buttons (password eye toggle).
        // Always target the real submit button to avoid replacing the eye with text.
        const button = this.querySelector('button[type="submit"]') || document.getElementById('signupBtn');
        if (button.disabled) return; // Prevent multiple submissions
        const originalHtml = button.innerHTML;
        const fullName = this.querySelector('input[name="fullName"]').value.trim();
        const contactNumber = this.querySelector('input[type="tel"]').value.trim();
        const emailValue = this.querySelector('input[type="email"]').value.trim();
        const password = document.getElementById('signupPassword') ? document.getElementById('signupPassword').value.trim() : '';
        if (!fullName || fullName.length < 2) { showToast('Please enter a valid name', 'error'); return; }
        if (!validateMobile(contactNumber)) { showToast('Please enter a valid 10-digit mobile number', 'error'); return; }
        if (!password || password.length < 4) { showToast('Please enter a valid password (min 4 chars)', 'error'); return; }
        
        let captchaToken = '';
        let captchaRequired = true;
        
        // Check if we're in development mode (localhost) - disable captcha
        if (window.location.hostname === 'localhost' || window.location.hostname === '127.0.0.1' || window.location.protocol === 'file:') {
            captchaRequired = false;
        }
        
        try { 
            if (window.grecaptcha && captchaRequired) {
                captchaToken = grecaptcha.getResponse(window.SIGNUP_RECAPTCHA_ID); 
            }
        } catch(_) {}
        
        if (captchaRequired && !captchaToken) { 
            showToast('Please complete reCAPTCHA', 'error'); 
            return; 
        }
        button.disabled = true;
        button.innerHTML = '<i class="fas fa-spinner fa-spin"></i> Signing up...';
        const payload = { fullName, contactNumber, password };
        if (emailValue) payload.email = emailValue;
        try {
            const headers = { 'Content-Type': 'application/json' };
            if (captchaRequired && captchaToken) {
                headers['X-Captcha-Token'] = captchaToken;
            }
            
            const res = await fetch(`${API_BASE_URL}/signup-direct`, {
                method: 'POST',
                headers: headers,
                body: JSON.stringify(payload)
            });
            const data = await handleApiResponse(res);
            showToast('Signup successful! Please login.', 'success');
            // Switch to login view
            this.closest('.signup-form').style.display = 'none';
            document.querySelector('.login-form').style.display = 'block';
            // Prefill phone on login
            const loginPhone = document.getElementById('loginContactNumber');
            if (loginPhone) loginPhone.value = contactNumber;
        } catch (err) {
            showToast(err.message || 'Signup failed', 'error');
        } finally {
            button.disabled = false;
            button.innerHTML = originalHtml;
            // Reset captcha for security
            try { if (window.grecaptcha) grecaptcha.reset(window.SIGNUP_RECAPTCHA_ID); } catch(_) {}
        }
    });
    
    function updateDebugInfo(element, message) {
        if (element) {
            element.textContent = message;
            element.style.display = 'block';
            console.log("Debug:", message);
        }
    }
    
    async function requestNewOTP(contactNumber, apiUrl, button) {
        try {
            button.disabled = true;
            button.textContent = 'Sending...';
            
            const form = button.closest('form');
            const otpGroup = form.querySelector('.otp-group');
            const otpInput = otpGroup.querySelector('input');
            otpInput.value = '';
            
            const payload = {
                contactNumber: contactNumber
            };
            
            let captchaToken = '';
            try { if (window.grecaptcha) captchaToken = grecaptcha.getResponse(); } catch(_) {}
            let response = await fetch(apiUrl, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json', 'X-Captcha-Token': captchaToken || '' },
                body: JSON.stringify(payload),
            });

            let data = await handleApiResponse(response);
            if (data) {
                showToast('New OTP sent successfully!');
                otpExpired = false;
                startOtpTimer(button, 30);
            }
        } catch (error) {
            button.disabled = false;
            button.textContent = 'Resend OTP';
            showToast(error.message || 'Failed to send new OTP! Try again.', 'error');
        }
    }
    
    function startOtpTimer(button, seconds) {
        if (otpTimer) {
            clearInterval(otpTimer);
        }
        timeRemaining = seconds;
        button.disabled = false;
        otpExpired = false;
        
        let timerDisplay = document.createElement('span');
        timerDisplay.className = 'otp-timer';
        const formatTime = (seconds) => {
            const mins = Math.floor(seconds / 60);
            const secs = seconds % 60;
            return `${mins}:${secs < 10 ? '0' : ''}${secs}`;
        };
        timerDisplay.textContent = `(${formatTime(timeRemaining)})`;
        timerDisplay.style.marginLeft = '10px';
        timerDisplay.style.fontSize = '14px';
        timerDisplay.style.color = '#666';
        button.textContent = 'Verify OTP';
        if (button.nextElementSibling && button.nextElementSibling.classList.contains('otp-timer')) {
            button.parentNode.removeChild(button.nextElementSibling);
        }
        button.parentNode.insertBefore(timerDisplay, button.nextSibling);
        otpTimer = setInterval(() => {
            timeRemaining--;
            timerDisplay.textContent = `(${formatTime(timeRemaining)})`;
            if (timeRemaining <= 0) {
                clearInterval(otpTimer);
                button.textContent = 'Resend OTP';
                otpExpired = true;
                if (timerDisplay.parentNode) {
                    timerDisplay.parentNode.removeChild(timerDisplay);
                }
                showToast('OTP expired. Please request a new one.', 'error');
            }
        }, 1000);
    }

    async function handleAuthProcess(form, apiUrl, type) {
        const mobileInput = form.querySelector('input[type="tel"]');
        const otpGroup = form.querySelector('.otp-group');
        const otpInput = otpGroup.querySelector('input');
        const button = form.querySelector('button');
        const debugInfo = form.querySelector('.debug-info');
        const allInputFields = form.querySelectorAll('input:not([type="checkbox"])');

        if (otpGroup.style.display === 'none') {
            if (validateMobile(mobileInput.value)) {
                try {
                    currentContactNumber = mobileInput.value.trim();
                    updateDebugInfo(debugInfo, `Processing ${type.toLowerCase()} request for ${currentContactNumber}...`);
                    if (type === 'Signup') {
                        try {
                            updateDebugInfo(debugInfo, `Checking if number ${currentContactNumber} already exists...`);
                            const checkResponse = await fetch(`${API_BASE_URL}/login`, {
                                method: 'POST',
                                headers: { 'Content-Type': 'application/json' },
                                body: JSON.stringify({ contactNumber: currentContactNumber }),
                            });
                            if (checkResponse.ok) {
                                const data = await checkResponse.json();
                                if (data.userExists === 'true') {
                                    updateDebugInfo(debugInfo, `Number ${currentContactNumber} is already registered`);
                                    showToast('This number is already registered. Please login instead.', 'error');
                                    return;
                                }
                            }
                        } catch (error) {
                            console.log('User existence check failed, continuing with signup');
                        }
                    }
                    const payload = { contactNumber: currentContactNumber };
                    if (type === 'Signup') {
                        payload.fullName = form.querySelector('input[type="text"]').value.trim();
                        const emailValue = form.querySelector('input[type="email"]').value.trim();
                        if (emailValue) {
                            const emailPattern = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
                            if (!emailPattern.test(emailValue)) {
                                showToast('Please enter a valid email address', 'error');
                                return;
                            }
                            payload.email = emailValue;
                        }
                        const pw = document.getElementById('signupPassword') ? document.getElementById('signupPassword').value.trim() : '';
                        if (!pw || pw.length < 4) { showToast('Please enter a valid password (min 4 chars)', 'error'); return; }
                        payload.password = pw;
                    }
                    button.disabled = true;
                    button.textContent = 'Sending...';
                    let captchaToken = '';
                    try { if (window.grecaptcha) captchaToken = grecaptcha.getResponse(type === 'Signup' ? window.SIGNUP_RECAPTCHA_ID : undefined); } catch(_) {}
                    if (type === 'Signup' && !captchaToken) {
                        showToast(type + ': Please complete reCAPTCHA before requesting OTP', 'error');
                        button.disabled = false;
                        button.textContent = 'Get OTP';
                        return;
                    }
                    let response = await fetch(apiUrl, {
                        method: 'POST',
                        headers: { 'Content-Type': 'application/json', ...(type==='Signup' ? {'X-Captcha-Token': captchaToken} : {}) },
                        body: JSON.stringify(payload),
                    });
                    let data = await handleApiResponse(response);
                    if (data) {
                        form.dataset.userExists = data.userExists || 'false';
                        if (type === 'Login' && data.userExists === 'false') {
                            showToast('User not found. Please sign up first.', 'error');
                            button.disabled = false;
                            button.textContent = 'Get OTP';
                            return;
                        }
                        otpGroup.style.display = 'block';
                        button.textContent = 'Verify OTP';
                        button.disabled = false;
                        showToast('OTP sent successfully!');
                        otpInput.focus();
                        allInputFields.forEach(input => { if (!input.closest('.otp-group')) { input.disabled = true; } });
                        startOtpTimer(button, 30);
                    }
                } catch (error) {
                    updateDebugInfo(debugInfo, `Error: ${error.message || 'Unknown error occurred'}`);
                    button.disabled = false;
                    button.textContent = 'Get OTP';
                    if (error.message && error.message.includes('User not found')) {
                        if (type === 'Login') { showToast('User not found. Please sign up first.', 'error'); }
                        else { showToast('Proceed with signup', 'success'); }
                    } else {
                        showToast(error.message || `Failed to process ${type.toLowerCase()}. Please try again later.`, 'error');
                    }
                }
            } else {
                showToast('Please enter a valid 10-digit mobile number', 'error');
            }
        } else {
            if (otpInput.value.length === 4) {
                try {
                    if (otpExpired) { showToast('This OTP has expired. Please request a new one.', 'error'); return; }
                    button.disabled = true; button.textContent = 'Verifying...';
                    const payload = { contactNumber: mobileInput.value.trim(), otp: otpInput.value.trim(), isSignup: type === 'Signup' && form.dataset.userExists === 'false' };
                    let response = await fetch(`${API_BASE_URL}/verify-otp`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(payload) });
                    let data = await handleApiResponse(response);
                    if (data) {
                        if (otpTimer) { clearInterval(otpTimer); otpTimer = null; }
                        showToast(data.message || 'Login successful!');
                        localStorage.setItem('isLoggedIn', 'true');
                        localStorage.setItem('userPhone', mobileInput.value.trim());
                        if (data.token) {
                            if (window.AuthToken && typeof window.AuthToken.set === 'function') {
                                window.AuthToken.set(data.token);
                            } else {
                                sessionStorage.setItem('authToken', data.token);
                                try { localStorage.removeItem('authToken'); } catch (_e) {}
                            }
                        }
                        localStorage.removeItem('userMembership');
                        let redirectPage = 'index';
                        if (type === 'Signup' && form.dataset.userExists === 'false') { redirectPage = 'register'; }
                        setTimeout(() => { 
                            const params = new URLSearchParams(window.location.search);
                            const redir = params.get('redirect');
                            if (redir === 'register') redirectPage = 'register';
                            if (redir === 'driver-dashboard') redirectPage = 'driver-dashboard';
                            window.location.href = redirectPage; 
                        }, 1500);
                    }
                } catch (error) {
                    button.disabled = false; button.textContent = 'Verify OTP';
                    showToast(error.message || 'OTP verification failed! Try again.', 'error');
                }
            } else {
                showToast('Please enter a valid 4-digit OTP', 'error');
            }
        }
    }

    async function handleApiResponse(response) {
        let data;
        try { data = await response.json(); }
        catch (error) {
            try { const text = await response.text(); throw new Error(text || 'Unexpected response from server!'); }
            catch (e) { throw new Error('Unexpected response from server!'); }
        }
        if (!response.ok) {
            if (data && data.message) { throw new Error(data.message); }
            else { throw new Error('Failed to process request. Please try again later.'); }
        }
        return data;
    }

    function showToast(message, type = 'success') {
        const toast = document.createElement('div');
        toast.className = `toast ${type}`;
        toast.textContent = message;
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
        toast.style.fontWeight = '500';
        toast.style.boxShadow = '0 4px 12px rgba(0,0,0,0.15)';
        toast.style.transition = 'all 0.3s ease';
        
        // Show toast immediately with animation
        toast.style.opacity = '0';
        toast.style.transform = 'translateX(-50%) translateY(20px)';
        
        // Force reflow and then animate in
        toast.offsetHeight;
        toast.style.opacity = '1';
        toast.style.transform = 'translateX(-50%) translateY(0)';
        
        // Remove after shorter duration for better UX
        setTimeout(() => { 
            if (document.body.contains(toast)) {
                toast.style.opacity = '0';
                toast.style.transform = 'translateX(-50%) translateY(-20px)';
                setTimeout(() => {
                    if (document.body.contains(toast)) {
                        document.body.removeChild(toast);
                    }
                }, 300);
            }
        }, 2500);
    }

    function validateMobile(number) { number = number.trim(); return /^[6-9]\d{9}$/.test(number); }

    // Name sanitizers
    document.querySelectorAll('input[name="fullName"]').forEach(input => {
        input.addEventListener('keypress', function (e) {
            const char = e.key;
            if (!char.match(/[a-zA-Z\s]/) || (this.value.length === 0 && char === ' ') || (char === ' ' && this.value.slice(-1) === ' ')) { e.preventDefault(); }
        });
        input.addEventListener('blur', function () { this.value = this.value.replace(/[^a-zA-Z\s]/g, '').replace(/\s+/g, ' ').trim(); });
    });

    document.querySelectorAll('input[type="tel"]').forEach(input => {
        input.addEventListener('keypress', function (e) { if (e.key < '0' || e.key > '9' || this.value.length >= 10) { e.preventDefault(); } });
    });

    function checkAuthStatus() {
        const isLoggedIn = localStorage.getItem('isLoggedIn') === 'true';
        const currentPage = window.location.pathname.split('/').pop();
        if (isLoggedIn && (currentPage === 'login')) { window.location.href = 'index'; }
    }
    checkAuthStatus();

    // Password show/hide text toggles
    document.querySelectorAll('.pw-toggle').forEach(t => {
        t.addEventListener('click', function(){
            const targetId = this.getAttribute('data-target');
            const input = document.getElementById(targetId);
            if (!input) return;
            const isPw = input.type === 'password';
            input.type = isPw ? 'text' : 'password';
            this.textContent = isPw ? 'Hide' : 'Show';
        });
    });

    // Ensure eye buttons are initialized and wired once
    (function initEyeButtons(){
        try{
            function sync(btn, input){
                const isVisible = input.type === 'text';
                const icon = btn.querySelector('i');
                // When visible, show "eye" (tap to hide). When hidden, show "eye-slash" (tap to show).
                if (icon) icon.className = isVisible ? 'fas fa-eye' : 'fas fa-eye-slash';
                btn.setAttribute('aria-pressed', String(isVisible));
                btn.setAttribute('aria-label', isVisible ? 'Show password' : 'Hide password');
            }
            document.querySelectorAll('.pw-eye[data-target]').forEach(function(btn){
                if (btn.dataset.init === '1') return;
                const id = btn.getAttribute('data-target');
                const input = id ? document.getElementById(id) : null;
                if (!input) return;
                // Add padding class so text doesn't sit under the eye
                if (!input.classList.contains('has-eye')) input.classList.add('has-eye');
                // Initial sync
                sync(btn, input);
                // Click handler per button (more reliable than global delegation)
                btn.addEventListener('click', function(ev){
                    ev.preventDefault();
                    ev.stopPropagation();
                    input.type = (input.type === 'password') ? 'text' : 'password';
                    sync(btn, input);
                });
                // If something else changes the type, resync icon
                input.addEventListener('change', function(){ sync(btn, input); });
                input.addEventListener('input', function(){ /* no-op, but keeps live */ });
                btn.dataset.init = '1';
            });
        }catch(_){/* no-op */}
    })();

    // OTP Login modal logic
    const forgotLink = document.getElementById('forgotPasswordLink');
    const otpModal = document.getElementById('otpLoginModal');
    if (forgotLink && otpModal) {
        // Method chooser modal
        const methodModal = document.getElementById('forgotMethodModal');
        const methodCloseBtn = document.getElementById('forgotMethodClose');
        const chooseMobileBtn = document.getElementById('chooseMobileOtpBtn');
        const chooseEmailBtn = document.getElementById('chooseEmailOtpBtn');

        // Email OTP modal
        const emailModal = document.getElementById('emailOtpModal');
        const emailCloseBtn = document.getElementById('emailOtpClose');
        const emailBackBtn = document.getElementById('emailOtpBack');
        const emailInput = document.getElementById('emailOtpEmail');
        const emailInputGroup = document.getElementById('emailOtpEmailGroup');
        const emailOtpGroup = document.getElementById('emailOtpCodeGroup');
        const emailOtpInput = document.getElementById('emailOtpCode');
        const emailOtpTimerEl = document.getElementById('emailOtpTimer');
        const emailResendRow = document.getElementById('emailOtpResendRow');
        const emailResendBtn = document.getElementById('emailOtpResendBtn');
        const emailPrimaryBtn = document.getElementById('emailOtpPrimaryBtn');
        const emailNewPassBlock = document.getElementById('emailOtpNewPassBlock');
        const emailNewPass = document.getElementById('emailOtpNewPassword');
        const emailConfirmPass = document.getElementById('emailOtpConfirmPassword');
        const emailSetNewPassBtn = document.getElementById('emailOtpSetNewPasswordBtn');

        const modalPhone = document.getElementById('otpLoginPhone');
        const modalOtp = document.getElementById('otpLoginOtp');
        const primaryBtn = document.getElementById('otpLoginPrimaryBtn');
        const otpBackBtn = document.getElementById('otpLoginBack');
        const otpTimerEl = document.getElementById('otpLoginTimer');
        const otpGroupEl = document.getElementById('otpLoginOtpGroup');
        const newPassBlock = document.getElementById('otpLoginNewPassBlock');
        const newPass = document.getElementById('otpNewPassword');
        const confirmPass = document.getElementById('otpConfirmPassword');
        const setNewPassBtn = document.getElementById('otpSetNewPasswordBtn');
        const loginFormEl = document.getElementById('loginForm');
        const mobileInputEl = loginFormEl.querySelector('input[type="tel"]');
        let wrongOtpCount = 0;
        let timerId = null;
        let timeLeft = 0;
        let isOtpSent = false;
        let otpFlowLocked = false; // prevents closing until password reset finishes

        // Email OTP state
        let emailTimerId = null;
        let emailTimeLeft = 0;
        let emailOtpSent = false;
        let emailResetToken = '';
        let currentForgotNumber = '';
        let lastInvalidEmailToast = '';
        let emailFlowLocked = false;

        function setEmailHeaderLock(locked){
            emailFlowLocked = !!locked;
            if (emailCloseBtn) emailCloseBtn.style.display = locked ? 'none' : '';
            if (emailBackBtn) emailBackBtn.style.display = locked ? 'none' : '';
        }

        function openModal() { 
            otpModal.style.display = 'flex'; 
            otpFlowLocked = true;
            if (otpBackBtn) otpBackBtn.style.display = '';
            // Pre-fill phone number from login form
            if (mobileInputEl && mobileInputEl.value) {
                modalPhone.value = mobileInputEl.value;
            }
        }
        
        function closeModal() { 
            otpModal.style.display = 'none'; 
            resetModalState(); 
            otpFlowLocked = false;
        }

        function openMethodModal(){
            if (!methodModal) return;
            methodModal.style.display = 'flex';
        }

        function closeMethodModal(){
            if (!methodModal) return;
            methodModal.style.display = 'none';
        }

        function backToMethodFromOtp(){
            closeModal();
            openMethodModal();
        }

        function backToMethodFromEmail(){
            closeEmailModal();
            openMethodModal();
        }

        function stopEmailTimer(){
            if (emailTimerId) {
                clearInterval(emailTimerId);
                emailTimerId = null;
            }
        }

        function startEmailTimer(seconds){
            stopEmailTimer();
            emailTimeLeft = seconds;
            if (emailOtpTimerEl) {
                emailOtpTimerEl.style.display = 'inline';
                emailOtpTimerEl.textContent = formatTime(emailTimeLeft);
            }
            if (emailResendBtn) emailResendBtn.disabled = true;
            emailTimerId = setInterval(()=>{
                emailTimeLeft--;
                if (emailOtpTimerEl) emailOtpTimerEl.textContent = formatTime(emailTimeLeft);
                if (emailTimeLeft <= 0) {
                    stopEmailTimer();
                    if (emailOtpTimerEl) emailOtpTimerEl.textContent = formatTime(0);
                    if (emailResendBtn) emailResendBtn.disabled = false;
                    emailOtpSent = false;
                }
            }, 1000);
        }

        function resetEmailModalState(){
            stopEmailTimer();
            emailOtpSent = false;
            emailResetToken = '';
            setEmailHeaderLock(false);
            if (emailInput) {
                emailInput.readOnly = false;
            }
            if (emailOtpTimerEl) emailOtpTimerEl.style.display = 'none';
            if (emailResendRow) emailResendRow.style.display = 'none';
            if (emailResendBtn) emailResendBtn.disabled = true;
            if (emailOtpInput) emailOtpInput.value = '';
            if (emailNewPass) emailNewPass.value = '';
            if (emailConfirmPass) emailConfirmPass.value = '';
            if (emailPrimaryBtn) {
                emailPrimaryBtn.disabled = true;
                emailPrimaryBtn.style.display = 'block';
                emailPrimaryBtn.textContent = 'Send OTP';
            }
            if (emailInputGroup) emailInputGroup.style.display = 'block';
            if (emailOtpGroup) emailOtpGroup.style.display = 'none';
            if (emailNewPassBlock) emailNewPassBlock.style.display = 'none';
            updateEmailPrimaryBtnState();
        }

        function openEmailModal(){
            if (!emailModal) return;
            resetEmailModalState();
            emailModal.style.display = 'flex';
            setTimeout(()=>{ try{ emailInput?.focus(); }catch(_){ } }, 0);
        }

        function closeEmailModal(){
            if (!emailModal) return;
            emailModal.style.display = 'none';
            resetEmailModalState();
        }
        
        function resetModalState(){ 
            modalOtp.value=''; 
            primaryBtn.textContent='Get OTP'; 
            primaryBtn.style.display='block';
            primaryBtn.disabled = false;
            newPassBlock.style.display='none'; 
            if(otpGroupEl){ 
                otpGroupEl.style.opacity = '0';
                otpGroupEl.style.transform = 'translateY(-10px)';
                setTimeout(() => {
                    if (otpGroupEl) {
                        otpGroupEl.style.display='none'; 
                    }
                }, 300);
            } 
            stopTimer(); 
            otpTimerEl.style.display='none'; 
            isOtpSent = false;
            try{
                grecaptcha.reset(window.OTPLOGIN_RECAPTCHA_ID);
            }catch(_){}
        }
        
        function startTimer(seconds){ 
            stopTimer(); 
            timeLeft = seconds; 
            otpTimerEl.style.display='inline'; 
            otpTimerEl.textContent = formatTime(timeLeft); 
            timerId = setInterval(()=>{ 
                timeLeft--; 
                otpTimerEl.textContent = formatTime(timeLeft); 
                if(timeLeft<=0){ 
                    stopTimer(); 
                    primaryBtn.textContent='Resend OTP'; 
                    isOtpSent = false;
                    showToast('OTP expired. Please request a new one.', 'error');
                } 
            }, 1000); 
        }
        
        function stopTimer(){ 
            if(timerId){ 
                clearInterval(timerId); 
                timerId=null; 
            } 
        }
        
        function formatTime(s){ 
            const m=Math.floor(s/60), ss=s%60; 
            return ` ${m}:${ss<10?'0':''}${ss}`; 
        }

        // Remove ability to close by clicking outside while locked
        otpModal.addEventListener('click', (e)=>{ if(!otpFlowLocked) { if(e.target===otpModal) closeModal(); } });

        if (methodCloseBtn && methodModal) {
            methodCloseBtn.addEventListener('click', function(){ closeMethodModal(); });
            methodModal.addEventListener('click', function(e){ if (e.target === methodModal) closeMethodModal(); });
        }

        if (emailCloseBtn && emailModal) {
            emailCloseBtn.addEventListener('click', function(){
                if (emailFlowLocked) return;
                closeEmailModal();
            });
            emailModal.addEventListener('click', function(e){
                if (emailFlowLocked) return;
                if (e.target === emailModal) closeEmailModal();
            });
        }

        if (emailBackBtn) {
            emailBackBtn.addEventListener('click', function(){
                if (emailFlowLocked) return;
                backToMethodFromEmail();
            });
        }

        if (otpBackBtn) {
            otpBackBtn.addEventListener('click', function(){
                backToMethodFromOtp();
            });
        }
        
        forgotLink.addEventListener('click', async function(e){
            e.preventDefault();
            // Prefer the verified number from the 2-step login flow (when password field is visible)
            const number = ((typeof verifiedContactNumber === 'string' && verifiedContactNumber)
                ? verifiedContactNumber
                : (mobileInputEl.value || '')).trim();
            if (!validateMobile(number)) { 
                showToast('Enter valid 10-digit mobile', 'error'); 
                return; 
            }
            // Correct existence check: do NOT call /api/users/{phone} (it requires auth and returns 403).
            // Use the public /auth/check-user endpoint.
            try {
                const res = await fetch(`${API_BASE_URL}/check-user`, {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ contactNumber: number })
                });
                const data = await res.json().catch(() => ({}));
                const exists = !!(data && (data.exists === true || data.exists === 'true'));
                if (!res.ok || !exists) {
                    showToast('Number not found. Please sign up first.', 'error');
                    return;
                }
            } catch(err){
                showToast('Unable to verify number. Try again.', 'error');
                return;
            }
            currentForgotNumber = number;
            modalPhone.value = number;
            wrongOtpCount = 0;
            openMethodModal();
        });

        if (chooseMobileBtn) {
            chooseMobileBtn.addEventListener('click', function(){
                closeMethodModal();
                openModal();
            });
        }

        if (chooseEmailBtn) {
            chooseEmailBtn.addEventListener('click', function(){
                closeMethodModal();
                openEmailModal();
            });
        }

        function normalizeEmail(email){
            return String(email || '').trim().toLowerCase();
        }

        function isValidEmail(email){
            const e = normalizeEmail(email);
            if (!e) return false;
            if (e.length > 254) return false;
            if (e.includes('..')) return false;
            const parts = e.split('@');
            if (parts.length !== 2) return false;
            const local = parts[0];
            const domain = parts[1];
            if (!local || !domain) return false;
            if (local.length > 64) return false;
            if (!domain.includes('.')) return false;
            if (domain.startsWith('.') || domain.endsWith('.')) return false;
            if (!/^[a-z0-9.!#$%&'*+/=?^_`{|}~-]+$/.test(local)) return false;
            if (!/^[a-z0-9.-]+$/.test(domain)) return false;
            const tld = domain.split('.').pop() || '';
            if (tld.length < 2) return false;
            return true;
        }

        function updateEmailPrimaryBtnState(){
            if (!emailPrimaryBtn) return;
            const label = (emailPrimaryBtn.textContent || '').trim();
            if (label !== 'Send OTP' && label !== 'Resend OTP') return;
            const ok = isValidEmail(emailInput?.value || '');
            emailPrimaryBtn.disabled = !ok;
        }

        async function requestEmailOtp(){
            if (!emailPrimaryBtn) return false;
            const email = normalizeEmail(emailInput?.value || '');
            if (!isValidEmail(email)) {
                showToast('Enter a valid email address', 'error');
                updateEmailPrimaryBtnState();
                return false;
            }
            if (!validateMobile(currentForgotNumber || '')) {
                showToast('Mobile number missing. Please try again.', 'error');
                return false;
            }

            emailPrimaryBtn.disabled = true;
            emailPrimaryBtn.innerHTML = '<i class="fas fa-spinner fa-spin" aria-hidden="true"></i> Sending...';
            try {
                const base = window.API_BASE_URL ? `${window.API_BASE_URL}/auth/forgot-email-init` : 'http://localhost:8080/auth/forgot-email-init';
                const res = await fetch(base, {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ contactNumber: currentForgotNumber, email })
                });
                const data = await res.json().catch(()=>({}));
                if (!res.ok || data.ok === false) {
                    showToast(data.message || 'Failed to send email OTP', 'error');
                    emailPrimaryBtn.disabled = false;
                    emailPrimaryBtn.textContent = 'Send OTP';
                    updateEmailPrimaryBtnState();
                    return false;
                }

                showToast('OTP sent to your email. Check inbox/spam.', 'success');
                if (emailInput) emailInput.readOnly = true;
                if (emailOtpGroup) emailOtpGroup.style.display = 'block';
                if (emailResendRow) emailResendRow.style.display = 'flex';
                if (emailOtpInput) emailOtpInput.focus();

                emailPrimaryBtn.disabled = false;
                emailPrimaryBtn.textContent = 'Verify OTP';
                startEmailTimer(60);
                emailOtpSent = true;
                return true;
            } catch (err) {
                showToast('Failed to send email OTP. Try again.', 'error');
                emailPrimaryBtn.disabled = false;
                emailPrimaryBtn.textContent = 'Send OTP';
                updateEmailPrimaryBtnState();
                return false;
            }
        }

        async function verifyEmailOtp(){
            const email = normalizeEmail(emailInput?.value || '');
            const otp = (emailOtpInput?.value || '').trim();
            if (!isValidEmail(email)) {
                showToast('Enter a valid email address', 'error');
                return;
            }
            if (!/^[0-9]{6}$/.test(otp)) {
                showToast('Please enter 6-digit OTP', 'error');
                return;
            }
            if (!emailPrimaryBtn) return;

            emailPrimaryBtn.disabled = true;
            emailPrimaryBtn.textContent = 'Verifying...';
            try {
                const base = window.API_BASE_URL ? `${window.API_BASE_URL}/auth/forgot-email-verify` : 'http://localhost:8080/auth/forgot-email-verify';
                const res = await fetch(base, {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ contactNumber: currentForgotNumber, email, otp })
                });
                const data = await res.json().catch(()=>({}));
                if (!res.ok || data.ok === false) {
                    showToast(data.message || 'Invalid OTP. Please try again.', 'error');
                    emailPrimaryBtn.disabled = false;
                    emailPrimaryBtn.textContent = 'Verify OTP';
                    return;
                }

                emailResetToken = data.resetToken || '';
                if (!emailResetToken) {
                    showToast('Verification failed. Please resend OTP.', 'error');
                    emailPrimaryBtn.disabled = false;
                    emailPrimaryBtn.textContent = 'Resend OTP';
                    return;
                }

                stopEmailTimer();
                if (emailInputGroup) emailInputGroup.style.display = 'none';
                if (emailOtpGroup) emailOtpGroup.style.display = 'none';
                if (emailResendRow) emailResendRow.style.display = 'none';
                if (emailPrimaryBtn) emailPrimaryBtn.style.display = 'none';
                if (emailOtpTimerEl) emailOtpTimerEl.style.display = 'none';
                if (emailNewPassBlock) emailNewPassBlock.style.display = 'block';
                setEmailHeaderLock(true);
                if (emailNewPass) emailNewPass.focus();
                showToast('OTP verified. Set your new password.', 'success');
            } catch (err) {
                showToast('Verification failed. Please try again.', 'error');
                emailPrimaryBtn.disabled = false;
                emailPrimaryBtn.textContent = 'Verify OTP';
            }
        }

        async function setNewPasswordByEmail(){
            const email = normalizeEmail(emailInput?.value || '');
            if (!emailResetToken) {
                showToast('Reset token missing. Please resend OTP.', 'error');
                return;
            }
            if (!emailNewPass || emailNewPass.value.length < 4) {
                showToast('Password must be at least 4 characters', 'error');
                return;
            }
            if (emailNewPass.value !== (emailConfirmPass?.value || '')) {
                showToast('Passwords do not match', 'error');
                return;
            }
            if (!emailSetNewPassBtn) return;

            emailSetNewPassBtn.disabled = true;
            emailSetNewPassBtn.textContent = 'Setting Password...';
            try {
                const base = window.API_BASE_URL ? `${window.API_BASE_URL}/auth/forgot-email-complete` : 'http://localhost:8080/auth/forgot-email-complete';
                const res = await fetch(base, {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({
                        contactNumber: currentForgotNumber,
                        email,
                        resetToken: emailResetToken,
                        newPassword: emailNewPass.value
                    })
                });
                const data = await res.json().catch(()=>({}));
                if (!res.ok || data.ok === false) {
                    showToast(data.message || 'Password reset failed', 'error');
                    emailSetNewPassBtn.disabled = false;
                    emailSetNewPassBtn.textContent = 'Set Password';
                    return;
                }
                showToast('Password reset successful! Please login.', 'success');
                setTimeout(()=>{ closeEmailModal(); }, 900);
            } catch (err) {
                showToast('Password reset failed. Please try again.', 'error');
                emailSetNewPassBtn.disabled = false;
                emailSetNewPassBtn.textContent = 'Set Password';
            }
        }

        if (emailPrimaryBtn) {
            emailPrimaryBtn.addEventListener('click', async function(){
                const label = emailPrimaryBtn.textContent.trim();
                if (label === 'Send OTP' || label === 'Resend OTP') {
                    await requestEmailOtp();
                } else if (label === 'Verify OTP') {
                    await verifyEmailOtp();
                }
            });
        }

        if (emailInput) {
            emailInput.addEventListener('input', function(){
                if (emailInput.readOnly) return;
                const before = emailInput.value;
                const normalized = before.replace(/\s+/g, '').toLowerCase();
                if (before !== normalized) emailInput.value = normalized;
                updateEmailPrimaryBtnState();
            });
            emailInput.addEventListener('blur', function(){
                const v = String(emailInput.value || '').trim();
                if (v && !isValidEmail(v) && v !== lastInvalidEmailToast) {
                    lastInvalidEmailToast = v;
                    showToast('Please enter a valid email (example: name@gmail.com)', 'error');
                }
                updateEmailPrimaryBtnState();
            });
        }

        if (emailResendBtn) {
            emailResendBtn.addEventListener('click', async function(){
                if (emailResendBtn.disabled) return;
                emailPrimaryBtn.textContent = 'Resend OTP';
                await requestEmailOtp();
            });
        }

        if (emailSetNewPassBtn) {
            emailSetNewPassBtn.addEventListener('click', setNewPasswordByEmail);
        }

        async function requestOtp() {
            // Disable button immediately to prevent multiple clicks and show loader
            primaryBtn.disabled = true;
            primaryBtn.innerHTML = '<i class="fas fa-spinner fa-spin" aria-hidden="true"></i> Sending...';
            
            let captchaToken = '';
            let captchaRequired = true;
            
            // Check if we're in development mode (localhost) - disable captcha
            if (window.location.hostname === 'localhost' || window.location.hostname === '127.0.0.1' || window.location.protocol === 'file:') {
                captchaRequired = false;
            }
            
            try { 
                if (window.grecaptcha && captchaRequired) {
                    captchaToken = grecaptcha.getResponse(window.OTPLOGIN_RECAPTCHA_ID); 
                }
            }catch(_) {}
            
            if (captchaRequired && !captchaToken) { 
                showToast('Please complete reCAPTCHA before requesting OTP', 'error'); 
                primaryBtn.disabled = false;
                primaryBtn.textContent = 'Get OTP';
                return false; 
            }
            
            try {
                const base = window.API_BASE_URL ? `${window.API_BASE_URL}/auth/forgot-init` : 'http://localhost:8080/auth/forgot-init';
                const res = await fetch(base, { 
                    method:'POST', 
                    headers:{
                        'Content-Type':'application/json',
                        'X-Captcha-Token':captchaToken
                    }, 
                    body: JSON.stringify({ contactNumber: modalPhone.value.trim() })
                });
                
                const data = await res.json().catch(()=>({}));
                
                if (!res.ok) { 
                    showToast(data.message || 'Failed to send OTP', 'error'); 
                    primaryBtn.disabled = false;
                    primaryBtn.textContent = 'Get OTP';
                    return false; 
                }
                
                // Show success message immediately
                showToast('OTP sent successfully! Please check your phone.', 'success');
                // Show bilingual popup: Please pick up voice call
                try{
                    const existing = document.getElementById('otpVoicePopup');
                    if (!existing) {
                        const overlay = document.createElement('div');
                        overlay.id = 'otpVoicePopup';
                        overlay.style.position = 'fixed';
                        overlay.style.inset = '0';
                        overlay.style.background = 'rgba(0,0,0,0.45)';
                        overlay.style.display = 'flex';
                        overlay.style.alignItems = 'center';
                        overlay.style.justifyContent = 'center';
                        overlay.style.zIndex = '1100';
                        const box = document.createElement('div');
                        box.style.background = '#fff';
                        box.style.borderRadius = '12px';
                        box.style.maxWidth = '520px';
                        box.style.width = '92%';
                        box.style.padding = '18px 16px';
                        box.style.boxShadow = '0 12px 32px rgba(0,0,0,.25)';
                        box.innerHTML = '<h3 style="margin:0 0 10px; font-size:18px;">Please pick up the voice call</h3>'+
                            '<p style="margin:0 0 8px; color:#444;">You will receive an automated call to hear your OTP.</p>'+
                            '<h3 style="margin:14px 0 8px; font-size:18px;">कृपया वॉयस कॉल उठाएँ</h3>'+
                            '<p style="margin:0; color:#444;">आपको आपका OTP सुनाने के लिए एक ऑटोमेटेड कॉल आएगी।</p>'+
                            '<div style="text-align:right; margin-top:14px;"><button id="otpVoicePopupClose" style="background:#4CAF50;color:#fff;border:none;border-radius:8px;padding:8px 14px;cursor:pointer;">OK</button></div>';
                        overlay.appendChild(box);
                        document.body.appendChild(overlay);
                        const close = () => { try{ document.body.removeChild(overlay); }catch(_){} };
                        overlay.addEventListener('click', (e)=>{ if(e.target===overlay) close(); });
                        overlay.querySelector('#otpVoicePopupClose').addEventListener('click', close);
                    }
                }catch(_){/* no-op */}
                
                // Show OTP field immediately with smooth animation
                if (otpGroupEl) { 
                    otpGroupEl.style.display = 'block';
                    // Force reflow for smooth animation
                    otpGroupEl.offsetHeight;
                    otpGroupEl.style.opacity = '1';
                    otpGroupEl.style.transform = 'translateY(0)';
                }
                
                // Change button to Verify OTP
                primaryBtn.textContent = 'Verify OTP';
                primaryBtn.disabled = false;
                
                // Focus on OTP input
                modalOtp.focus();
                
                // Start 1 minute timer
                startTimer(60);
                
                isOtpSent = true;
                return true;
                
            } catch (error) {
                showToast('Failed to send OTP. Please try again.', 'error');
                primaryBtn.disabled = false;
                primaryBtn.textContent = 'Get OTP';
                return false;
            }
        }

        async function verifyOtp() {
            const otp = modalOtp.value.trim();
            if (otp.length !== 4) { 
                showToast('Please enter 4-digit OTP', 'error'); 
                return; 
            }
            
            // Disable button during verification
            primaryBtn.disabled = true;
            primaryBtn.textContent = 'Verifying...';
            
            try {
                const baseV = window.API_BASE_URL ? `${window.API_BASE_URL}/auth/forgot-verify` : 'http://localhost:8080/auth/forgot-verify';
                const resV = await fetch(baseV, { 
                    method:'POST', 
                    headers:{
                        'Content-Type':'application/json'
                    }, 
                    body: JSON.stringify({ 
                        contactNumber: modalPhone.value.trim(), 
                        otp 
                    })
                });
                
                const dV = await resV.json().catch(()=>({}));
                
                if (!resV.ok) {
                    wrongOtpCount++;
                    showToast(dV.message || 'Invalid OTP. Please try again.', 'error');
                    
                    // On wrong OTP: change to Resend, reset captcha, allow resend
                    primaryBtn.textContent = 'Resend OTP';
                    primaryBtn.disabled = false;
                    primaryBtn.style.display = 'block';
                    
                    try { 
                        if (window.grecaptcha) grecaptcha.reset(window.OTPLOGIN_RECAPTCHA_ID); 
                    }catch(_){ }
                    
                    stopTimer();
                    isOtpSent = false;
                    
                    if (wrongOtpCount >= 3) {
                        alert("You've entered an incorrect OTP multiple times.\nFor your safety, we've redirected you to the homepage. Please try again later or contact support if you're facing issues.\n\n🔒 Your security is our priority");
                        window.location.href = 'index';
                    }
                    return;
                }
                
                // OTP verified successfully -> show clean new password UI only
                stopTimer();
                // Hide phone and OTP inputs area
                const phoneRow = modalPhone ? modalPhone.closest('.form-group') : null;
                const otpRow = otpGroupEl ? otpGroupEl.closest('.form-group') : null;
                if (phoneRow) phoneRow.style.display = 'none';
                if (otpRow) otpRow.style.display = 'none';
                // Hide reCAPTCHA block entirely
                try{
                    const recaptchaBlock = document.getElementById('otpLoginRecaptcha')?.closest('div');
                    if (recaptchaBlock) recaptchaBlock.style.display = 'none';
                    if (window.grecaptcha && window.OTPLOGIN_RECAPTCHA_ID) {
                        grecaptcha.reset(window.OTPLOGIN_RECAPTCHA_ID);
                    }
                }catch(_){}
                // Hide primary button and timer
                primaryBtn.style.display = 'none';
                const timerEl = document.getElementById('otpLoginTimer');
                if (timerEl) timerEl.style.display = 'none';
                // Show new password block
                newPassBlock.style.display = 'block';
                if (otpBackBtn) otpBackBtn.style.display = 'none';
                // Focus new password field for immediate input
                if (newPass) newPass.focus();
                showToast('OTP verified successfully! Please set your new password.', 'success');
                
            } catch (error) {
                showToast('Verification failed. Please try again.', 'error');
                primaryBtn.disabled = false;
                primaryBtn.textContent = 'Verify OTP';
            }
        }

        async function setNewPassword() {
            const otp = modalOtp.value.trim();
            if (!otp || otp.length !== 4) { 
                showToast('OTP missing/invalid', 'error'); 
                return; 
            }
            
            if (newPass.value.length < 4) {
                showToast('Password must be at least 4 characters', 'error');
                return;
            }
            
            if (newPass.value !== confirmPass.value) { 
                showToast('Passwords do not match', 'error'); 
                return; 
            }
            
            // Disable button during password reset
            setNewPassBtn.disabled = true;
            setNewPassBtn.textContent = 'Setting Password...';
            
            try {
                const base2 = window.API_BASE_URL ? `${window.API_BASE_URL}/auth/forgot-complete` : 'http://localhost:8080/auth/forgot-complete';
                const res2 = await fetch(base2, { 
                    method:'POST', 
                    headers:{
                        'Content-Type':'application/json'
                    }, 
                    body: JSON.stringify({ 
                        contactNumber: modalPhone.value.trim(), 
                        otp, 
                        newPassword: newPass.value 
                    })
                });
                
                const d2 = await res2.json().catch(()=>({}));
                
                if (!res2.ok) { 
                    showToast(d2.message || 'Password reset failed', 'error'); 
                    setNewPassBtn.disabled = false;
                    setNewPassBtn.textContent = 'Set Password';
                    return; 
                }
                
                showToast('Password reset successful! Please login with your new password.', 'success');
                
                // Redirect to login by closing modal
                setTimeout(()=> { 
                    otpFlowLocked = false; // allow closing now
                    closeModal(); 
                }, 1000);
                
            } catch (error) {
                showToast('Password reset failed. Please try again.', 'error');
                setNewPassBtn.disabled = false;
                setNewPassBtn.textContent = 'Set Password';
            }
        }

        primaryBtn.addEventListener('click', async function(){
            const label = primaryBtn.textContent.trim();
            if (label === 'Get OTP' || label === 'Resend OTP') { 
                await requestOtp(); 
            }
            else if (label === 'Verify OTP') { 
                await verifyOtp(); 
            }
        });
        
        setNewPassBtn.addEventListener('click', setNewPassword);
    }
});
