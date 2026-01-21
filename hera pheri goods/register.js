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
    const useLocationBtn = document.getElementById('useLocationBtn');
    const vehicleSelectBtn = document.getElementById('vehicleSelectBtn');
    const vehicleDropdown = document.querySelector('.vehicle-dropdown');
    const celebrationOverlay = document.getElementById('celebrationOverlay');
    const stateInput = document.getElementById('state');
    const districtSelect = document.getElementById('district');
    const pincodeSuggestions = document.getElementById('pincodeSuggestions');
    const pincodeChevron = document.getElementById('pincodeChevron');
    const vehicleNumberField = document.getElementById('vehicleNumber');
    const vehicleNumberContainer = vehicleNumberField.parentNode;

    // Vehicle Category gating (register page)
    const vehicleCategoryRegInput = document.getElementById('vehicleCategoryReg');
    const vehicleCategoryGroup = document.getElementById('vehicleCategoryGroup');
    const vehicleCategoryCards = Array.from(document.querySelectorAll('#vehicleCategoryGroup .vehicle-category-card'));
    
    // Track pincode verification status
    let isPincodeVerified = false;
    let lastVerifiedPin = null;

    function normalizeStateKey(s) {
        return String(s || '')
            .toLowerCase()
            .replace(/&/g, ' and ')
            .replace(/[^a-z0-9]+/g, ' ')
            .replace(/\s+/g, ' ')
            .trim();
    }

    function setStateFromValue(rawState) {
        if (!stateInput) return;
        const stateValue = String(rawState || '').trim();
        if (!stateValue) { stateInput.selectedIndex = 0; return; }

        // 1) Exact match (fast path)
        for (let i = 0; i < stateInput.options.length; i++) {
            if (stateInput.options[i].value === stateValue) {
                stateInput.selectedIndex = i;
                return;
            }
        }

        // 2) Tolerant match: '&' vs 'and', punctuation, NCT of Delhi, merged UT naming
        const wanted = normalizeStateKey(stateValue);
        const aliases = new Set([wanted]);
        if (wanted === 'jammu and kashmir') {
            aliases.add('jammu kashmir');
        }
        if (wanted === 'delhi') {
            aliases.add('nct of delhi');
        }
        if (wanted === 'dadra and nagar haveli and daman and diu') {
            aliases.add('daman and diu');
            aliases.add('dadra and nagar haveli');
        }

        for (let i = 0; i < stateInput.options.length; i++) {
            const optKey = normalizeStateKey(stateInput.options[i].value);
            if (aliases.has(optKey)) {
                stateInput.selectedIndex = i;
                return;
            }
        }
    }

    function escapeHtml(value) {
        const s = (value === null || value === undefined) ? '' : String(value);
        return s
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;')
            .replace(/'/g, '&#39;');
    }

    // ===== Pincode Suggestions CSS (scoped) =====
    (function injectPinStyles(){
        if (document.getElementById('reg-pin-suggestions-style')) return;
        const styleEl = document.createElement('style');
        styleEl.id = 'reg-pin-suggestions-style';
        styleEl.textContent = `
            .pin-suggestions {
                position: absolute;
                width: 100%;
                max-height: 320px;
                overflow-y: auto;
                background: #ffffff;
                color: #111827;
                border: 1px solid #e5e7eb;
                border-radius: 12px;
                box-shadow: 0 18px 40px rgba(0,0,0,0.18);
                z-index: 2000;
                margin-top: 8px;
                padding: 6px 0;
            }
            .pin-suggestion-item {
                padding: 12px 16px;
                display: flex;
                justify-content: space-between;
                align-items: center;
                gap: 12px;
                cursor: pointer;
                transition: background 0.15s ease, border-color 0.15s ease;
                border-left: 3px solid transparent;
            }
            .pin-suggestion-item:hover, .pin-suggestion-item.active {
                background: #f3f4f6;
                border-left-color: #3b82f6;
            }
            .pin-suggestion-code { font-weight: 700; letter-spacing: 0.3px; color:#111827; }
            .pin-suggestion-name { color:#6b7280; font-size: 14px; }
            .pin-chevron { position:absolute; right:12px; top:50%; transform: translateY(-50%); width:34px; height:34px; border:none; background:transparent; color:#6b7280; display:flex; align-items:center; justify-content:center; cursor:pointer; }
            .pin-chevron:hover { color:#374151; }
            #pincode.pin-verified { border-color:#10b981 !important; box-shadow: 0 0 0 4px rgba(16,185,129,0.15) !important; background:#ffffff; }
        `;
        document.head.appendChild(styleEl);
    })();

    // ===== State → District mapping (used for dropdown) =====
    // Minimal: districts are used for pincode suggestions; keep list consistent with Vehicles page.
    const stateToDistricts = {
        "Uttar Pradesh": ["Agra", "Aligarh", "Ambedkar Nagar", "Amethi", "Amroha", "Auraiya", "Ayodhya", "Azamgarh", "Baghpat", "Bahraich", "Ballia", "Balrampur", "Banda", "Barabanki", "Bareilly", "Basti", "Bhadohi", "Bijnor", "Budaun", "Bulandshahr", "Chandauli", "Chitrakoot", "Deoria", "Etah", "Etawah", "Farrukhabad", "Fatehpur", "Firozabad", "Gautam Buddha Nagar", "Ghaziabad", "Ghazipur", "Gonda", "Gorakhpur", "Hamirpur", "Hapur", "Hardoi", "Hathras", "Jalaun", "Jaunpur", "Jhansi", "Kannauj", "Kanpur Dehat", "Kanpur Nagar", "Kasganj", "Kaushambi", "Kushinagar", "Lakhimpur Kheri", "Lalitpur", "Lucknow", "Maharajganj", "Mahoba", "Mainpuri", "Mathura", "Mau", "Meerut", "Mirzapur", "Moradabad", "Muzaffarnagar", "Pilibhit", "Pratapgarh", "Prayagraj", "Raebareli", "Rampur", "Saharanpur", "Sambhal", "Sant Kabir Nagar", "Shahjahanpur", "Shamli", "Shravasti", "Siddharthnagar", "Sitapur", "Sonbhadra", "Sultanpur", "Unnao", "Varanasi"],
        "Maharashtra": ["Ahmednagar", "Akola", "Amravati", "Aurangabad", "Beed", "Bhandara", "Buldhana", "Chandrapur", "Dhule", "Gadchiroli", "Gondia", "Hingoli", "Jalgaon", "Jalna", "Kolhapur", "Latur", "Mumbai City", "Mumbai Suburban", "Nagpur", "Nanded", "Nandurbar", "Nashik", "Osmanabad", "Palghar", "Parbhani", "Pune", "Raigad", "Ratnagiri", "Sangli", "Satara", "Sindhudurg", "Solapur", "Thane", "Wardha", "Washim", "Yavatmal"],
        "Delhi": ["Central Delhi", "East Delhi", "New Delhi", "North Delhi", "North East Delhi", "North West Delhi", "Shahdara", "South Delhi", "South East Delhi", "South West Delhi", "West Delhi"],
        "Gujarat": ["Ahmedabad", "Amreli", "Anand", "Aravalli", "Banaskantha", "Bharuch", "Bhavnagar", "Botad", "Chhota Udaipur", "Dahod", "Dang", "Devbhoomi Dwarka", "Gandhinagar", "Gir Somnath", "Jamnagar", "Junagadh", "Kheda", "Kutch", "Mahisagar", "Mehsana", "Morbi", "Narmada", "Navsari", "Panchmahal", "Patan", "Porbandar", "Rajkot", "Sabarkantha", "Surat", "Surendranagar", "Tapi", "Vadodara", "Valsad"],
        "Rajasthan": ["Ajmer", "Alwar", "Banswara", "Baran", "Barmer", "Bharatpur", "Bhilwara", "Bikaner", "Bundi", "Chittorgarh", "Churu", "Dausa", "Dholpur", "Dungarpur", "Hanumangarh", "Jaipur", "Jaisalmer", "Jalore", "Jhalawar", "Jhunjhunu", "Jodhpur", "Karauli", "Kota", "Nagaur", "Pali", "Pratapgarh", "Rajsamand", "Sawai Madhopur", "Sikar", "Sirohi", "Sri Ganganagar", "Tonk", "Udaipur"],
        "Haryana": ["Ambala", "Bhiwani", "Charkhi Dadri", "Faridabad", "Fatehabad", "Gurugram", "Hisar", "Jhajjar", "Jind", "Kaithal", "Karnal", "Kurukshetra", "Mahendragarh", "Nuh", "Palwal", "Panchkula", "Panipat", "Rewari", "Rohtak", "Sirsa", "Sonipat", "Yamunanagar"],
        "Punjab": ["Amritsar", "Barnala", "Bathinda", "Faridkot", "Fatehgarh Sahib", "Fazilka", "Ferozepur", "Gurdaspur", "Hoshiarpur", "Jalandhar", "Kapurthala", "Ludhiana", "Mansa", "Moga", "Mohali", "Muktsar", "Pathankot", "Patiala", "Rupnagar", "Sangrur", "Shahid Bhagat Singh Nagar", "Tarn Taran"],
        "Andhra Pradesh": ["Anantapur", "Chittoor", "East Godavari", "Guntur", "Krishna", "Kurnool", "Nellore", "Prakasam", "Srikakulam", "Visakhapatnam", "Vizianagaram", "West Godavari", "YSR Kadapa"],
        "Karnataka": ["Bagalkot", "Ballari", "Belagavi", "Bengaluru Rural", "Bengaluru Urban", "Bidar", "Chamarajanagar", "Chikballapur", "Chikkamagaluru", "Chitradurga", "Dakshina Kannada", "Davanagere", "Dharwad", "Gadag", "Hassan", "Haveri", "Kalaburagi", "Kodagu", "Kolar", "Koppal", "Mandya", "Mysuru", "Raichur", "Ramanagara", "Shivamogga", "Tumakuru", "Udupi", "Uttara Kannada", "Vijayapura", "Yadgir"],
        "Tamil Nadu": ["Ariyalur", "Chengalpattu", "Chennai", "Coimbatore", "Cuddalore", "Dharmapuri", "Dindigul", "Erode", "Kallakurichi", "Kanchipuram", "Kanyakumari", "Karur", "Krishnagiri", "Madurai", "Mayiladuthurai", "Nagapattinam", "Namakkal", "Nilgiris", "Perambalur", "Pudukkottai", "Ramanathapuram", "Ranipet", "Salem", "Sivaganga", "Tenkasi", "Thanjavur", "Theni", "Thoothukudi", "Tiruchirappalli", "Tirunelveli", "Tirupathur", "Tiruppur", "Tiruvallur", "Tiruvannamalai", "Tiruvarur", "Vellore", "Viluppuram", "Virudhunagar"],
        "West Bengal": ["Alipurduar", "Bankura", "Birbhum", "Cooch Behar", "Dakshin Dinajpur", "Darjeeling", "Hooghly", "Howrah", "Jalpaiguri", "Jhargram", "Kalimpong", "Kolkata", "Malda", "Murshidabad", "Nadia", "North 24 Parganas", "Paschim Bardhaman", "Paschim Medinipur", "Purba Bardhaman", "Purba Medinipur", "Purulia", "South 24 Parganas", "Uttar Dinajpur"],
        "Kerala": ["Alappuzha", "Ernakulam", "Idukki", "Kannur", "Kasaragod", "Kollam", "Kottayam", "Kozhikode", "Malappuram", "Palakkad", "Pathanamthitta", "Thiruvananthapuram", "Thrissur", "Wayanad"],
        "Telangana": ["Adilabad", "Bhadradri Kothagudem", "Hyderabad", "Jagtial", "Jangaon", "Jayashankar Bhupalpally", "Jogulamba Gadwal", "Kamareddy", "Karimnagar", "Khammam", "Komaram Bheem", "Mahabubabad", "Mahabubnagar", "Mancherial", "Medak", "Medchal-Malkajgiri", "Mulugu", "Nagarkurnool", "Nalgonda", "Narayanpet", "Nirmal", "Nizamabad", "Peddapalli", "Rajanna Sircilla", "Rangareddy", "Sangareddy", "Siddipet", "Suryapet", "Vikarabad", "Wanaparthy", "Warangal Rural", "Warangal Urban", "Yadadri Bhuvanagiri"],
        "Madhya Pradesh": ["Agar Malwa", "Alirajpur", "Anuppur", "Ashoknagar", "Balaghat", "Barwani", "Betul", "Bhind", "Bhopal", "Burhanpur", "Chhatarpur", "Chhindwara", "Damoh", "Datia", "Dewas", "Dhar", "Dindori", "Guna", "Gwalior", "Harda", "Hoshangabad", "Indore", "Jabalpur", "Jhabua", "Katni", "Khandwa", "Khargone", "Mandla", "Mandsaur", "Morena", "Narsinghpur", "Neemuch", "Niwari", "Panna", "Raisen", "Rajgarh", "Ratlam", "Rewa", "Sagar", "Satna", "Sehore", "Seoni", "Shahdol", "Shajapur", "Sheopur", "Shivpuri", "Sidhi", "Singrauli", "Tikamgarh", "Ujjain", "Umaria", "Vidisha"],
        "Bihar": ["Araria", "Arwal", "Aurangabad", "Banka", "Begusarai", "Bhagalpur", "Bhojpur", "Buxar", "Darbhanga", "East Champaran", "Gaya", "Gopalganj", "Jamui", "Jehanabad", "Kaimur", "Katihar", "Khagaria", "Kishanganj", "Lakhisarai", "Madhepura", "Madhubani", "Munger", "Muzaffarpur", "Nalanda", "Nawada", "Patna", "Purnia", "Rohtas", "Saharsa", "Samastipur", "Saran", "Sheikhpura", "Sheohar", "Sitamarhi", "Siwan", "Supaul", "Vaishali", "West Champaran"],
        "Odisha": ["Angul", "Balangir", "Balasore", "Bargarh", "Bhadrak", "Boudh", "Cuttack", "Deogarh", "Dhenkanal", "Gajapati", "Ganjam", "Jagatsinghpur", "Jajpur", "Jharsuguda", "Kalahandi", "Kandhamal", "Kendrapara", "Kendujhar", "Khordha", "Koraput", "Malkangiri", "Mayurbhanj", "Nabarangpur", "Nayagarh", "Nuapada", "Puri", "Rayagada", "Sambalpur", "Subarnapur", "Sundergarh"],
        "Jharkhand": ["Bokaro", "Chatra", "Deoghar", "Dhanbad", "Dumka", "East Singhbhum", "Garhwa", "Giridih", "Godda", "Gumla", "Hazaribagh", "Jamtara", "Khunti", "Koderma", "Latehar", "Lohardaga", "Pakur", "Palamu", "Ramgarh", "Ranchi", "Sahebganj", "Seraikela Kharsawan", "Simdega", "West Singhbhum"],
        "Assam": ["Baksa", "Barpeta", "Biswanath", "Bongaigaon", "Cachar", "Charaideo", "Chirang", "Darrang", "Dhemaji", "Dhubri", "Dibrugarh", "Dima Hasao", "Goalpara", "Golaghat", "Hailakandi", "Hojai", "Jorhat", "Kamrup", "Kamrup Metropolitan", "Karbi Anglong", "Karimganj", "Kokrajhar", "Lakhimpur", "Majuli", "Morigaon", "Nagaon", "Nalbari", "Sivasagar", "Sonitpur", "South Salmara-Mankachar", "Tinsukia", "Udalguri", "West Karbi Anglong"],
        "Chhattisgarh": ["Balod", "Baloda Bazar", "Balrampur", "Bastar", "Bemetara", "Bijapur", "Bilaspur", "Dantewada", "Dhamtari", "Durg", "Gariaband", "Gaurela Pendra Marwahi", "Janjgir Champa", "Jashpur", "Kabirdham", "Kanker", "Kondagaon", "Korba", "Koriya", "Mahasamund", "Mungeli", "Narayanpur", "Raigarh", "Raipur", "Rajnandgaon", "Sukma", "Surajpur", "Surguja"],
        "Uttarakhand": ["Almora", "Bageshwar", "Chamoli", "Champawat", "Dehradun", "Haridwar", "Nainital", "Pauri Garhwal", "Pithoragarh", "Rudraprayag", "Tehri Garhwal", "Udham Singh Nagar", "Uttarkashi"],
        "Himachal Pradesh": ["Bilaspur", "Chamba", "Hamirpur", "Kangra", "Kinnaur", "Kullu", "Lahaul and Spiti", "Mandi", "Shimla", "Sirmaur", "Solan", "Una"],
        "Goa": ["North Goa", "South Goa"],
        "Arunachal Pradesh": ["Anjaw", "Changlang", "Dibang Valley", "East Kameng", "East Siang", "Kamle", "Kra Daadi", "Kurung Kumey", "Lepa Rada", "Lohit", "Longding", "Lower Dibang Valley", "Lower Siang", "Lower Subansiri", "Namsai", "Pakke Kessang", "Papum Pare", "Shi Yomi", "Siang", "Tawang", "Tirap", "Upper Siang", "Upper Subansiri", "West Kameng", "West Siang"],
        "Manipur": ["Bishnupur", "Chandel", "Churachandpur", "Imphal East", "Imphal West", "Jiribam", "Kakching", "Kamjong", "Kangpokpi", "Noney", "Pherzawl", "Senapati", "Tamenglong", "Tengnoupal", "Thoubal", "Ukhrul"],
        "Meghalaya": ["East Garo Hills", "East Jaintia Hills", "East Khasi Hills", "North Garo Hills", "Ri Bhoi", "South Garo Hills", "South West Garo Hills", "South West Khasi Hills", "West Garo Hills", "West Jaintia Hills", "West Khasi Hills"],
        "Mizoram": ["Aizawl", "Champhai", "Hnahthial", "Khawzawl", "Kolasib", "Lawngtlai", "Lunglei", "Mamit", "Saiha", "Saitual", "Serchhip"],
        "Nagaland": ["Dimapur", "Kiphire", "Kohima", "Longleng", "Mokokchung", "Mon", "Noklak", "Peren", "Phek", "Tuensang", "Wokha", "Zunheboto"],
        "Sikkim": ["East Sikkim", "North Sikkim", "South Sikkim", "West Sikkim"],
        "Tripura": ["Dhalai", "Gomati", "Khowai", "North Tripura", "Sepahijala", "South Tripura", "Unakoti", "West Tripura"],

        // Union Territories
        "Andaman and Nicobar Islands": ["Nicobar", "North and Middle Andaman", "South Andaman", "South Nicobar"],
        "Chandigarh": ["Chandigarh"],
        "Dadra and Nagar Haveli and Daman and Diu": ["Dadra and Nagar Haveli", "Daman", "Diu"],
        "Lakshadweep": ["Lakshadweep"],
        "Puducherry": ["Puducherry", "Karaikal", "Mahe", "Yanam"],
        "Jammu and Kashmir": ["Jammu", "Samba", "Kathua", "Udhampur", "Reasi", "Rajouri", "Poonch", "Doda", "Kishtwar", "Ramban", "Anantnag", "Bandipora", "Baramulla", "Budgam", "Ganderbal", "Kulgam", "Kupwara", "Pulwama", "Shopian", "Srinagar"],
        "Ladakh": ["Leh", "Kargil"]
    };

    // Puducherry UT: local-only pincode list and pin mapping (no API)
    const PUDUCHERRY_LOCAL = {
        districts: {
            'Puducherry': [
                { pincode: '605001', postOfficeName: 'Puducherry HO' },
                { pincode: '605007', postOfficeName: 'Ariyankuppam' },
                { pincode: '605110', postOfficeName: 'Villianur' },
                { pincode: '605008', postOfficeName: 'Lawspet' },
                { pincode: '605014', postOfficeName: 'Pondicherry University' }
            ],
            'Karaikal': [
                { pincode: '609602', postOfficeName: 'Karaikal HO' }
            ],
            'Mahe': [
                { pincode: '673310', postOfficeName: 'Mahe HO' }
            ],
            'Yanam': [
                { pincode: '533464', postOfficeName: 'Yanam HO' }
            ]
        },
        pinToDistrict: {
            '605001': 'Puducherry',
            '605007': 'Puducherry',
            '605110': 'Puducherry',
            '605008': 'Puducherry',
            '605014': 'Puducherry',
            '609602': 'Karaikal',
            '673310': 'Mahe',
            '533464': 'Yanam'
        }
    };

    // Toggle bilingual hint on every click
    let pincodeHintToggle = 0;
    
    // Add variable to track if selected vehicle is a manual cart
    let isManualCartSelected = false;

    function shakeVehicleCategory() {
        const el = vehicleCategoryGroup || document.getElementById('vehicleCategoryGroup');
        if (!el) return;
        el.classList.remove('shake-attention');
        void el.offsetWidth;
        el.classList.add('shake-attention');
        window.setTimeout(() => el.classList.remove('shake-attention'), 600);
    }

    function setActiveVehicleCategory(value) {
        const val = String(value || '').toLowerCase();

        // Update hidden input (submitted)
        if (vehicleCategoryRegInput) vehicleCategoryRegInput.value = val;

        // Visual state
        vehicleCategoryCards.forEach(card => {
            const match = (String(card.getAttribute('data-category-value') || '').toLowerCase() === val);
            card.classList.toggle('active', match);
            card.setAttribute('aria-pressed', match ? 'true' : 'false');
        });

        // Filter vehicle types by selected category
        if (vehicleDropdown) {
            if (val) vehicleDropdown.setAttribute('data-active-category', val);
            else vehicleDropdown.removeAttribute('data-active-category');
        }

        // Enable/disable vehicle type dropdown button
        if (vehicleSelectBtn) {
            const enabled = !!val;
            vehicleSelectBtn.classList.toggle('is-disabled', !enabled);
            vehicleSelectBtn.setAttribute('aria-disabled', enabled ? 'false' : 'true');
        }

        // Reset chosen vehicle type when category changes
        try {
            const typeInputs = document.querySelectorAll('input[name="vehicleType"]');
            typeInputs.forEach(i => { i.checked = false; });
            if (vehicleSelectBtn) vehicleSelectBtn.querySelector('span').textContent = 'Select Vehicle Type';
            if (vehicleDropdown) vehicleDropdown.classList.remove('show');
            isManualCartSelected = false;
            if (vehicleNumberContainer) {
                vehicleNumberContainer.style.display = 'block';
                if (vehicleNumberField) vehicleNumberField.setAttribute('required', 'required');
            }
        } catch(_) {}
    }
    
    // Add helper text for vehicle number format
    const vehicleNumberHelper = document.createElement('div');
    vehicleNumberHelper.className = 'vehicle-number-helper';
    vehicleNumberHelper.innerHTML = 'Format: XX-00-XX-0000 (e.g., MH-01-AB-1234)';
    vehicleNumberHelper.style.color = '#666';
    vehicleNumberHelper.style.fontSize = '12px';
    vehicleNumberHelper.style.marginTop = '5px';
    vehicleNumberContainer.appendChild(vehicleNumberHelper);
    
    // Enable state/district selection (vehicles.html-like behavior)
    try {
        if (stateInput) { stateInput.removeAttribute('disabled'); stateInput.style.backgroundColor = ''; }
        if (districtSelect) { districtSelect.disabled = true; }
    } catch(_) {}

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

    // If user tries to interact without pincode (optional helper)
    if (districtSelect) {
        districtSelect.addEventListener('focus', function() {
            if (isPincodeVerified) return;
            // no-op: keep UX clean
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
    locationNote.innerHTML = 'Select State/District or enter your pincode';
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

    // Vehicle Category cards: click/keyboard to select
    if (vehicleCategoryCards.length) {
        vehicleCategoryCards.forEach(card => {
            card.addEventListener('click', () => setActiveVehicleCategory(card.getAttribute('data-category-value') || ''));
            card.addEventListener('keydown', (e) => {
                if (e.key === 'Enter' || e.key === ' ') {
                    e.preventDefault();
                    setActiveVehicleCategory(card.getAttribute('data-category-value') || '');
                }
            });
        });
    }
    // Initialize default state
    setActiveVehicleCategory(vehicleCategoryRegInput ? vehicleCategoryRegInput.value : '');

    // Vehicle dropdown toggle (gated by category)
    vehicleSelectBtn.addEventListener('click', function() {
        const hasCategory = !!(vehicleCategoryRegInput && vehicleCategoryRegInput.value);
        if (!hasCategory) {
            showToast('Please select vehicle category first', 'error');
            shakeVehicleCategory();
            return;
        }
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

    function clearPincodeVerified() {
        isPincodeVerified = false;
        lastVerifiedPin = null;
        try {
            if (pincode) {
                pincode.classList.remove('pin-verified');
                pincode.style.borderColor = '';
                pincode.style.boxShadow = '';
            }
        } catch(_) {}

        // Also reset the helper text so "Location verified" doesn't stick after edits
        try {
            if (typeof locationNote !== 'undefined' && locationNote) {
                locationNote.innerHTML = 'Select State/District or enter your pincode';
                locationNote.style.color = '#777';
            }
        } catch(_) {}
    }

    function setChevronOpen(isOpen) {
        if (!pincodeChevron) return;
        const icon = pincodeChevron.querySelector('i');
        if (icon) {
            icon.classList.toggle('fa-chevron-up', !!isOpen);
            icon.classList.toggle('fa-chevron-down', !isOpen);
        }
        pincodeChevron.setAttribute('aria-expanded', isOpen ? 'true' : 'false');
    }

    function updateChevronVisibility() {
        if (!pincodeChevron) return;
        const hasList = !!(pincodeSuggestions && pincodeSuggestions.children && pincodeSuggestions.children.length);
        const canShow = hasList && !isPincodeVerified && !!(stateInput && stateInput.value) && !!(districtSelect && districtSelect.value);
        pincodeChevron.style.display = canShow ? 'inline-flex' : 'none';
        if (!canShow) setChevronOpen(false);
    }

    function setDistrictOptionsForState(stateValue) {
        if (!districtSelect) return;
        const st = String(stateValue || '').trim();
        districtSelect.innerHTML = '<option value="">Select District</option>';
        districtSelect.disabled = true;
        if (st && stateToDistricts[st]) {
            stateToDistricts[st].forEach(d => {
                const opt = document.createElement('option');
                opt.value = d;
                opt.textContent = d;
                districtSelect.appendChild(opt);
            });
            districtSelect.disabled = false;
        }
    }

    // Initialize district list (in case state is pre-filled)
    if (stateInput && districtSelect) {
        setDistrictOptionsForState(stateInput.value);
    }

    function showPincodeList(list) {
        if (!pincodeSuggestions) return;
        pincodeSuggestions.innerHTML = '';
        list.forEach(o => {
            const item = document.createElement('div');
            item.className = 'pin-suggestion-item';
            item.innerHTML = `<div class="pin-suggestion-code">${escapeHtml(o.pincode || o.Pincode || '')}</div><div class="pin-suggestion-name">${escapeHtml(o.postOfficeName || o.Name || '')}</div>`;
            item.addEventListener('click', () => {
                const pin = String(o.pincode || o.Pincode || '').trim();
                if (!pin) return;
                pincode.value = pin;
                isPincodeVerified = true;
                lastVerifiedPin = pin;
                if (pincodeChevron) {
                    setChevronOpen(false);
                    pincodeChevron.style.display = 'none';
                }
                if (pincode) {
                    pincode.classList.add('pin-verified');
                    pincode.style.borderColor = '#10b981';
                    pincode.style.boxShadow = '0 0 0 4px rgba(16,185,129,0.15)';
                    pincode.placeholder = 'Enter pincode';
                }
                pincodeSuggestions.style.display = 'none';
                locationNote.innerHTML = 'Location selected';
                locationNote.style.color = '#4CAF50';
            });
            pincodeSuggestions.appendChild(item);
        });
        pincodeSuggestions.style.display = 'none';
        setChevronOpen(false);
        updateChevronVisibility();
    }

    async function fetchPincodesForSelection(stateValue, districtValue) {
        const selectedState = String(stateValue || '').trim();
        const selectedDistrict = String(districtValue || '').trim();
        if (!selectedDistrict) return [];

        const selectedStateKey = normalizeStateKey(selectedState);
        const selectedDistrictKey = normalizeStateKey(selectedDistrict);

        // Puducherry local-only (no API)
        if (selectedStateKey === 'puducherry') {
            return (PUDUCHERRY_LOCAL.districts[selectedDistrict] || []).slice();
        }

        const matchesSelectedState = (officeState) => {
            const key = normalizeStateKey(officeState);
            if (!selectedStateKey) return true;
            if (key === selectedStateKey) return true;
            if (selectedStateKey === 'delhi' && key === 'nct of delhi') return true;
            if (selectedStateKey === 'ladakh' && key === 'jammu and kashmir') return true;
            if (selectedStateKey === 'andaman and nicobar islands' && (key === 'andaman and nicobar' || key === 'andaman nicobar' || key === 'andaman nicobar islands')) return true;
            if (selectedStateKey === 'puducherry' && key === 'pondicherry') return true;
            // Puducherry enclaves can appear under neighbouring states in India Post
            if (selectedStateKey === 'puducherry') {
                if (selectedDistrictKey === 'mahe' && key === 'kerala') return true;
                if (selectedDistrictKey === 'yanam' && key === 'andhra pradesh') return true;
                if (selectedDistrictKey === 'karaikal' && key === 'tamil nadu') return true;
            }
            // India Post may still report old UT names for merged UT
            if (selectedStateKey === 'dadra and nagar haveli and daman and diu' && (key === 'dadra and nagar haveli' || key === 'daman and diu')) return true;
            return false;
        };

        const isUnionTerritory = (s) => {
            const k = normalizeStateKey(s);
            return [
                'andaman and nicobar islands',
                'chandigarh',
                'dadra and nagar haveli and daman and diu',
                'delhi',
                'jammu and kashmir',
                'ladakh',
                'lakshadweep',
                'puducherry'
            ].includes(k);
        };

        const getIndiaPostOffices = async (q) => {
            try {
                const resp = await fetch(`https://api.postalpincode.in/postoffice/${encodeURIComponent(q)}`);
                const json = await resp.json();
                return (json && json[0] && json[0].Status === 'Success') ? (json[0].PostOffice || []) : [];
            } catch (_) {
                return [];
            }
        };

        const stateSearchAlias = (s, district) => {
            const k = normalizeStateKey(s);
            const d = normalizeStateKey(district);
            if (k === 'andaman and nicobar islands') return 'Andaman';
            if (k === 'puducherry') {
                if (d === 'puducherry') return 'Pondicherry';
                return district || 'Pondicherry';
            }
            if (k === 'lakshadweep') return 'Kavaratti';
            if (k === 'dadra and nagar haveli and daman and diu') {
                if (d.includes('daman')) return 'Daman';
                if (d.includes('diu')) return 'Diu';
                return 'Silvassa';
            }
            if (k === 'ladakh') return 'Leh';
            if (k === 'delhi') return 'Delhi';
            if (k === 'jammu and kashmir') return 'Kashmir';
            return s;
        };

        const buildPincodeListFromOffices = (offices, district) => {
            const dKey = normalizeStateKey(district);
            const stateKey = normalizeStateKey(selectedState);
            const districtAliases = new Set([dKey]);
            if (stateKey === 'puducherry' && dKey === 'puducherry') districtAliases.add('pondicherry');
            if (stateKey === 'andaman and nicobar islands' && dKey === 'north and middle andaman') districtAliases.add('north middle andaman');
            if (stateKey === 'andaman and nicobar islands' && dKey === 'south nicobar') districtAliases.add('nicobar');
            if (stateKey === 'dadra and nagar haveli and daman and diu' && dKey === 'dadra and nagar haveli') {
                districtAliases.add('dadra nagar haveli');
                districtAliases.add('dadra & nagar haveli');
            }

            const filtered = offices
                .filter(o => !selectedState || matchesSelectedState(o.State))
                .filter(o => {
                    if (!district) return true;
                    const od = normalizeStateKey(o.District || o.Division || o.Region || '');
                    return districtAliases.has(od);
                });

            const unique = new Map();
            filtered.forEach(o => { if (o && o.Pincode) unique.set(o.Pincode, o); });
            return Array.from(unique.values());
        };

        // 1) Try backend first (cleanest)
        const apiBase = (window.API_BASE_URL || '').replace(/\/$/, '');
        if (apiBase) {
            try {
                const resp = await fetch(`${apiBase}/api/geo/pincodes?district=${encodeURIComponent(selectedDistrict)}&state=${encodeURIComponent(selectedState)}`);
                if (resp.ok) {
                    const data = await resp.json();
                    if (data && Array.isArray(data.pincodes) && data.pincodes.length) {
                        return data.pincodes.map(p => ({ pincode: String(p.pincode || ''), postOfficeName: p.postOfficeName || '' }));
                    }
                }
            } catch (_) {
                // fall through
            }
        }

        // 2) India Post fallback with filtering (prevents mixed state/district pincodes)
        // 2.1) Try searching by district name
        let offices = await getIndiaPostOffices(selectedDistrict);
        let list = buildPincodeListFromOffices(offices, selectedDistrict);

        // 2.2) If empty (common for UT districts), try searching by state alias and filter by district
        if (!list.length && isUnionTerritory(selectedState)) {
            if (normalizeStateKey(selectedState) === 'lakshadweep') {
                const queries = ['Kavaratti', 'Agatti', 'Amini', 'Minicoy', 'Andrott', 'Kalpeni', 'Kadmat', 'Kiltan', 'Chetlat', 'Bitra'];
                const all = [];
                for (const q of queries) {
                    const part = await getIndiaPostOffices(q);
                    if (part && part.length) all.push(...part);
                }
                list = buildPincodeListFromOffices(all, selectedDistrict);
            } else {
                offices = await getIndiaPostOffices(stateSearchAlias(selectedState, selectedDistrict));
                list = buildPincodeListFromOffices(offices, selectedDistrict);
            }
        }

        return list;
    }

    async function verifyPinAndAutofill(pin) {
        const p = String(pin || '').trim();
        if (!/^\d{6}$/.test(p)) return false;

        // Puducherry local-only pins
        const localDistrict = PUDUCHERRY_LOCAL.pinToDistrict[p];
        if (localDistrict) {
            setStateFromValue('Puducherry');
            setDistrictOptionsForState('Puducherry');
            if (districtSelect) {
                districtSelect.value = localDistrict;
            }
            isPincodeVerified = true;
            lastVerifiedPin = p;
            if (pincode) {
                pincode.classList.add('pin-verified');
                pincode.style.borderColor = '#10b981';
                pincode.style.boxShadow = '0 0 0 4px rgba(16,185,129,0.15)';
            }
            locationNote.innerHTML = 'Location verified';
            locationNote.style.color = '#4CAF50';
            return true;
        }

        // If user selected Puducherry, do not use any external API for manual verification
        if (stateInput && normalizeStateKey(stateInput.value) === 'puducherry') {
            return false;
        }

        try {
            const resp = await fetch(`https://api.postalpincode.in/pincode/${encodeURIComponent(p)}`);
            const data = await resp.json();
            if (!(data && data[0] && data[0].Status === 'Success' && data[0].PostOffice && data[0].PostOffice.length)) return false;
            const postOffice = data[0].PostOffice[0];
            let st = postOffice.State || postOffice.Circle || '';
            const dist = postOffice.District || postOffice.Division || postOffice.Region || postOffice.Block || '';

            // Ladakh: India Post sometimes reports J&K for Leh/Kargil
            if (normalizeStateKey(st) === 'jammu and kashmir') {
                const dKey = normalizeStateKey(dist);
                if (dKey === 'leh' || dKey === 'kargil') st = 'Ladakh';
            }

            setStateFromValue(st);
            setDistrictOptionsForState(stateInput.value);
            if (districtSelect && dist) {
                // add option if not in list
                const has = Array.from(districtSelect.options).some(o => (o.value || '').toLowerCase() === String(dist).toLowerCase());
                if (!has) {
                    const opt = document.createElement('option');
                    opt.value = dist;
                    opt.textContent = dist;
                    districtSelect.appendChild(opt);
                }
                districtSelect.value = dist;
            }

            isPincodeVerified = true;
            lastVerifiedPin = p;
            if (pincode) {
                pincode.classList.add('pin-verified');
                pincode.style.borderColor = '#10b981';
                pincode.style.boxShadow = '0 0 0 4px rgba(16,185,129,0.15)';
            }
            locationNote.innerHTML = 'Location verified';
            locationNote.style.color = '#4CAF50';
            return true;
        } catch (e) {
            return false;
        }
    }

    // State → District
    if (stateInput && districtSelect) {
        stateInput.addEventListener('change', function() {
            clearPincodeVerified();
            if (pincodeSuggestions) { pincodeSuggestions.innerHTML = ''; pincodeSuggestions.style.display = 'none'; }
            if (pincodeChevron) pincodeChevron.style.display = 'none';
            if (pincode) {
                pincode.value = '';
                pincode.placeholder = 'Pincode';
            }
            setDistrictOptionsForState(this.value);
        });
    }

    // District → Pincode suggestions
    if (districtSelect && pincode) {
        districtSelect.addEventListener('change', async function() {
            clearPincodeVerified();
            const st = stateInput ? stateInput.value : '';
            const dist = this.value;
            if (pincodeSuggestions) { pincodeSuggestions.innerHTML = ''; pincodeSuggestions.style.display = 'none'; }
            if (pincodeChevron) { setChevronOpen(false); pincodeChevron.style.display = 'none'; }
            if (pincode) {
                pincode.value = '';
                pincode.placeholder = (st && dist) ? 'Loading pincodes...' : 'Pincode';
            }
            if (!st || !dist) return;
            const list = await fetchPincodesForSelection(st, dist);
            showPincodeList(list);
            if (pincode) {
                pincode.placeholder = list && list.length ? 'Select Pincode' : 'Enter pincode manually';
            }
        });
    }

    // Pincode input: digits only + auto-verify at 6 digits
    if (pincode) {
        pincode.addEventListener('input', function() {
            this.value = this.value.replace(/[^0-9]/g, '');
            if (this.value.length > 6) this.value = this.value.slice(0, 6);
            const val = (this.value || '').trim();
            // If user edits even 1 digit, remove verified state immediately
            if (lastVerifiedPin && val === lastVerifiedPin) {
                // still verified; keep chevron hidden
                updateChevronVisibility();
                return;
            }
            clearPincodeVerified();
            updateChevronVisibility();
            if (/^\d{6}$/.test(val)) {
                // Auto-verify
                verifyPinAndAutofill(val).then(ok => {
                    if (ok) showToast('Pincode verified successfully!', 'success');
                    else {
                        locationNote.innerHTML = 'Could not verify pincode. Please recheck or select from list.';
                        locationNote.style.color = '#ff5252';
                    }
                });
            }
        });

        // Toggle suggestions via chevron (only when state+district selected)
        const toggleSuggestions = () => {
            if (isPincodeVerified) return;
            if (!stateInput || !districtSelect) return;
            if (!stateInput.value || !districtSelect.value) return;
            updateChevronVisibility();
            if (pincodeChevron && pincodeChevron.style.display === 'none') return;
            if (pincodeSuggestions && pincodeSuggestions.children.length) {
                const nextOpen = (pincodeSuggestions.style.display === 'none' || !pincodeSuggestions.style.display);
                pincodeSuggestions.style.display = nextOpen ? 'block' : 'none';
                setChevronOpen(nextOpen);
            }
        };
        if (pincodeChevron) pincodeChevron.addEventListener('click', toggleSuggestions);

        // Close suggestions when clicking outside
        document.addEventListener('mousedown', (e) => {
            try {
                if (!pincodeSuggestions) return;
                if (pincodeSuggestions.style.display !== 'block') return;
                const wrap = pincode.closest('.pincode-select-wrapper') || pincode.parentElement;
                if (wrap && wrap.contains(e.target)) return;
                pincodeSuggestions.style.display = 'none';
                setChevronOpen(false);
            } catch(_) {}
        }, true);

        // Keyboard navigation
        pincode.addEventListener('keydown', (e) => {
            if (!pincodeSuggestions || !pincodeSuggestions.children.length) return;
            const items = Array.from(pincodeSuggestions.children);
            let idx = items.findIndex(x => x.classList.contains('active'));
            if (e.key === 'ArrowDown') { e.preventDefault(); idx = (idx < items.length-1) ? idx+1 : 0; items.forEach(i=>i.classList.remove('active')); items[idx].classList.add('active'); items[idx].scrollIntoView({block:'nearest'}); }
            if (e.key === 'ArrowUp') { e.preventDefault(); idx = (idx > 0) ? idx-1 : items.length-1; items.forEach(i=>i.classList.remove('active')); items[idx].classList.add('active'); items[idx].scrollIntoView({block:'nearest'}); }
            if (e.key === 'Enter' && idx >= 0) { e.preventDefault(); items[idx].click(); }
        });
    }

    // Use Location flow: detect GPS -> reverse geocode -> autofill -> auto-verify
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
                        // Fill state/district if available
                        if (data.state) { setStateFromValue(data.state); setDistrictOptionsForState(stateInput.value); }
                        if (data.district && districtSelect) {
                            const has = Array.from(districtSelect.options).some(o => (o.value || '').toLowerCase() === String(data.district).toLowerCase());
                            if (!has) {
                                const opt = document.createElement('option');
                                opt.value = data.district;
                                opt.textContent = data.district;
                                districtSelect.appendChild(opt);
                            }
                            districtSelect.value = data.district;
                        }
                        // Fill pincode and auto-verify
                        pincode.value = String(data.pincode).slice(0,6);
                        const ok = await verifyPinAndAutofill(pincode.value);
                        if (ok) {
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
    
    // Function to show toast message
    function showToast(message, type = 'success') {
        const toast = document.createElement('div');
        toast.className = `toast ${type}`;

        // Safely render line breaks for messages joined with "<br>" without using innerHTML
        const raw = String(message ?? '');
        toast.textContent = '';
        const parts = raw.split(/<br\s*\/?>/i);
        parts.forEach((part, idx) => {
            toast.appendChild(document.createTextNode(part));
            if (idx < parts.length - 1) toast.appendChild(document.createElement('br'));
        });
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
        const hasMultipleErrors = /<br\s*\/?>/i.test(String(message ?? ''));
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

        // Scroll helper: bring first invalid field into viewport
        const scrollToElement = (el) => {
            if (!el) return;
            const target = el.closest('.form-group') || el;
            try {
                const top = target.getBoundingClientRect().top + window.pageYOffset - 120;
                window.scrollTo({ top, behavior: 'smooth' });
            } catch(_) {}
            // Prefer focusing a real input/select if possible
            try {
                if (typeof el.focus === 'function') {
                    el.focus({ preventScroll: true });
                    return;
                }
            } catch(_) {}
            try {
                const focusable = target.querySelector('input,select,textarea,button,[tabindex]');
                if (focusable && typeof focusable.focus === 'function') {
                    focusable.focus({ preventScroll: true });
                }
            } catch(_) {}
        };

        let firstInvalidTarget = null;
        const setFirstInvalid = (el) => {
            if (!firstInvalidTarget && el) firstInvalidTarget = el;
        };

        // Check required fields
        const requiredFields = form.querySelectorAll('[required]');
        requiredFields.forEach(field => {
            if (!field.value) {
                isValid = false;
                field.classList.add('error');

                // Remember first missing required field (skip hidden inputs)
                try {
                    const isHidden = field.type === 'hidden' || field.offsetParent === null;
                    if (!isHidden) setFirstInvalid(field);
                } catch(_) { setFirstInvalid(field); }
                
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
            setFirstInvalid(contactNo);
        }
        
        // Validate WhatsApp number length only if provided
        if (whatsappNo.value.length > 0 && whatsappNo.value.length < 10) {
            isValid = false;
            whatsappNo.classList.add('error');
            errorMessages.push('WhatsApp number must be 10 digits if provided');
            setFirstInvalid(whatsappNo);
        } else {
            whatsappNo.classList.remove('error');
        }
        
        // Validate pincode verification
        if (!isPincodeVerified) {
            isValid = false;
            pincode.classList.add('error');
            errorMessages.push('Please verify your pincode before submitting');
            setFirstInvalid(pincode);
        }

        // Validate vehicle category selection
        if (!(vehicleCategoryRegInput && vehicleCategoryRegInput.value)) {
            isValid = false;
            errorMessages.push('Please select a vehicle category');
            shakeVehicleCategory();
            setFirstInvalid(vehicleCategoryGroup || document.getElementById('vehicleCategoryGroup'));
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
            setFirstInvalid(vehicleSelectBtn);
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

            // Scroll to the first missing upload box
            try {
                const firstMissingView = photoViews.find(v => !uploadedPhotos[v]);
                if (firstMissingView) {
                    const firstBox = document.querySelector(`.photo-upload-box[data-view="${firstMissingView}"]`);
                    setFirstInvalid(firstBox);
                }
            } catch(_) {}
            
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
            setFirstInvalid(termsCheckbox);
        } else {
            termsCheckbox.parentNode.style.color = '';
            termsCheckbox.parentNode.style.fontWeight = '';
        }
        
        if (!privacyCheckbox.checked) {
            isValid = false;
            errorMessages.push('Please agree to the Privacy Policy');
            privacyCheckbox.parentNode.style.color = '#d9534f';
            privacyCheckbox.parentNode.style.fontWeight = 'bold';
            setFirstInvalid(privacyCheckbox);
        } else {
            privacyCheckbox.parentNode.style.color = '';
            privacyCheckbox.parentNode.style.fontWeight = '';
        }

        // Validate vehicle number format if not a manual cart
        if (!isManualCartSelected && vehicleNumberField.value) {
            if (!validateVehicleNumber(vehicleNumberField, false)) {
                isValid = false;
                errorMessages.push('Invalid vehicle number format');
                setFirstInvalid(vehicleNumberField);
            }
        }

        if (!isValid) {
            // Display all error messages in a single toast
            showToast(errorMessages.join('<br>'), 'error');

            // Bring user to the first invalid field
            if (firstInvalidTarget) {
                scrollToElement(firstInvalidTarget);
            }
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
            localStorage.removeItem('userMembership');
            localStorage.removeItem('authToken');
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
            showToast('Please enter a valid pincode (auto-verified) before submitting', 'error');
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
        // District dropdown posts as `city` for backend compatibility
        const districtEl = document.getElementById('district');
        formData.append('city', districtEl ? districtEl.value : '');
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
