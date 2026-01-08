// AI Assistant for Hera-pheri-goods (production trimmed)
// Generate a unique session ID for this conversation
const sessionId = 'session_' + Date.now() + '_' + Math.random().toString(36).substring(2, 9);

// Variables to track vehicle type and pincode mentioned in conversation
let mentionedVehicleType = null;
let mentionedPincode = null;

// Track if FAQ section was manually collapsed by user
let faqManuallyCollapsed = false;

// API base URL - normalized to avoid double /api
const API_BASE_URL = (function(){
  const base = window.API_BASE_URL || 'http://localhost:8080';
  return base.replace(/\/+$/,'') + '/api/ai';
})();
// Support email (avoid plaintext in source HTML)
const SUPPORT_USER = 'info';
const SUPPORT_DOMAIN = 'herapherigoods.in';
const SUPPORT_AT = String.fromCharCode(64); // '@' without using the literal to avoid plaintext email in source
const SUPPORT_EMAIL = SUPPORT_USER + SUPPORT_AT + SUPPORT_DOMAIN;

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

// List of vehicle types to detect in conversations
const vehicleTypes = [
  "Manual Cart", "Thel", "Rickshaw", "Auto Loader", "CNG loader", "Tata Ace", "Chhota Hathi", 
  "E-Rickshaw", "Tuk-Tuk", "Mini Truck", "Eicher Canter", "Vikram Tempo", "Bolero Pickup", 
  "MaXX", "Tata 407", "6 wheeler", "Container Truck", "Open Body Truck", "Closed Body Truck", 
  "Flatbed Truck", "8 wheeler", "12 wheeler and onwards", "JCB", "Crane", "Trailer Truck", "Tipper Truck", 
  "Dumper Truck", "Tanker Truck", "Garbage Truck", "Ambulance", "Refrigerated Van", 
  "Refrigerated Truck", "Packers&Movers", "Parcel Delivery", "Tow-Truck", "Food trucks", "Beverage trucks"
];

// Regex pattern to detect pincodes (6 digits)
const pincodePattern = /\b\d{6}\b/;

// Sample training data for common questions and responses
const trainingData = {
  // Vehicle recommendation questions
  "which vehicle should i use for dairy products": {
    en: "For dairy products, I recommend using Refrigerated Vans or Refrigerated Trucks depending on the quantity. These vehicles maintain the required temperature for dairy products during transit.",
    hi: "डेयरी उत्पादों के लिए, मैं मात्रा के आधार पर रेफ्रिजरेटेड वैन या रेफ्रिजरेटेड ट्रक का उपयोग करने की सलाह देता हूं। ये वाहन परिवहन के दौरान डेयरी उत्पादों के लिए आवश्यक तापमान बनाए रखते हैं।"
  },
  "ice cream delivery ke liye konsa vehicle best hai": {
    en: "For ice cream delivery, Refrigerated Vans or Refrigerated Trucks are best as they maintain freezing temperatures. For smaller quantities, a Refrigerated Van would be sufficient.",
    hi: "आइसक्रीम डिलीवरी के लिए, रेफ्रिजरेटेड वैन या रेफ्रिजरेटेड ट्रक सबसे अच्छे हैं क्योंकि वे फ्रीजिंग तापमान बनाए रखते हैं। छोटी मात्रा के लिए, एक रेफ्रिजरेटेड वैन पर्याप्त होगी।"
  },
  "furniture shifting": {
    en: "For furniture shifting, I recommend using Packers & Movers service. They have specialized vehicles like Tata 407 or Mini Trucks with proper equipment for safe furniture transport.",
    hi: "फर्नीचर शिफ्टिंग के लिए, मैं पैकर्स एंड मूवर्स सेवा का उपयोग करने की सलाह देता हूं। उनके पास टाटा 407 या मिनी ट्रक जैसे विशेष वाहन होते हैं जिनमें फर्नीचर को सुरक्षित रूप से ट्रांसपोर्ट करने के लिए उचित उपकरण होते हैं।"
  },
  "construction material": {
    en: "For construction materials, depending on the quantity, you can use Open Body Trucks, Tipper Trucks (for sand, gravel), Dumper Trucks or Mini Trucks for smaller loads. Please specify the material type and quantity for a more accurate recommendation.",
    hi: "निर्माण सामग्री के लिए, मात्रा के आधार पर, आप ओपन बॉडी ट्रक, टिपर ट्रक (रेत, बजरी के लिए), या छोटे लोड के लिए मिनी ट्रक का उपयोग कर सकते हैं। अधिक सटीक सिफारिश के लिए कृपया सामग्री प्रकार और मात्रा निर्दिष्ट करें।"
  },
  "Transport vehicle": {
    en: "Tell me your vehicle type and pincode to find nearby transport vehicles for you.",
    hi: "अपने वाहन का प्रकार और पिनकोड बताएं ताकि आपके आस-पास उपलब्ध ट्रांसपोर्ट वाहन खोज सकें।"
  },
  
  // New vehicle type queries
  "thela": {
    en: "Manual Cart (Thela/Rickshaw) is good for very short distances within narrow lanes. It's useful for small loads in congested areas where larger vehicles can't go. Please share your pincode to check availability.",
    hi: "मैनुअल कार्ट (ठेला/रिक्शा) संकरी गलियों में बहुत कम दूरी के लिए अच्छा है। यह भीड़भाड़ वाले क्षेत्रों में छोटे लोड के लिए उपयोगी है जहां बड़े वाहन नहीं जा सकते। उपलब्धता जांचने के लिए कृपया अपना पिनकोड साझा करें।"
  },
  
  "rickshaw": {
    en: "Manual Cart (Thela/Rickshaw) is good for very short distances within narrow lanes. It's useful for small loads in congested areas where larger vehicles can't go. Please share your pincode to check availability.",
    hi: "मैनुअल कार्ट (ठेला/रिक्शा) संकरी गलियों में बहुत कम दूरी के लिए अच्छा है। यह भीड़भाड़ वाले क्षेत्रों में छोटे लोड के लिए उपयोगी है जहां बड़े वाहन नहीं जा सकते। उपलब्धता जांचने के लिए कृपया अपना पिनकोड साझा करें।"
  },
  
  "auto": {
    en: "Auto Loader (CNG loader) is good for medium-sized goods within the city. It's economical and can navigate through city traffic easily. Please share your pincode to check availability.",
    hi: "ऑटो लोडर (CNG लोडर) शहर के भीतर मध्यम आकार के सामान के लिए अच्छा है। यह किफायती है और शहर के ट्रैफिक में आसानी से चल सकता है। उपलब्धता जांचने के लिए कृपया अपना पिनकोड साझा करें।"
  },
  
  "chhota hathi": {
    en: "Tata Ace (Chhota Hathi) is a popular choice for small business deliveries. It's versatile and can carry loads up to 750kg. Please share your pincode to check availability.",
    hi: "टाटा एस (छोटा हाथी) छोटे व्यापार डिलीवरी के लिए एक लोकप्रिय विकल्प है। यह बहुमुखी है और 750 किलोग्राम तक का भार ले जा सकता है। उपलब्धता जांचने के लिए कृपया अपना पिनकोड साझा करें।"
  },
  
  "tuk tuk": {
    en: "E-Rickshaw Loader (Tuk-Tuk) is an eco-friendly option for local cargo. It's suitable for light loads and short distances within the city. Please share your pincode to check availability.",
    hi: "ई-रिक्शा लोडर (टुक-टुक) स्थानीय कार्गो के लिए एक पर्यावरण अनुकूल विकल्प है। यह शहर के भीतर हल्के लोड और कम दूरी के लिए उपयुक्त है। उपलब्धता जांचने के लिए कृपया अपना पिनकोड साझा करें।"
  },
  
  "mini truck": {
    en: "Mini Truck (Eicher Canter) is perfect for city-to-city transport. It can carry larger loads than smaller vehicles and is good for medium-distance transport. Please share your pincode to check availability.",
    hi: "मिनी ट्रक (आइशर कैंटर) शहर से शहर के परिवहन के लिए एकदम सही है। यह छोटे वाहनों की तुलना में बड़े लोड ले जा सकता है और मध्यम दूरी के परिवहन के लिए अच्छा है। उपलब्धता जांचने के लिए कृपया अपना पिनकोड साझा करें।"
  },
  
  "tempo": {
    en: "Vikram Tempo is good for local goods movement. It's suitable for medium loads within the city or nearby areas. Please share your pincode to check availability.",
    hi: "विक्रम टेम्पो स्थानीय सामान के आवागमन के लिए अच्छा है। यह शहर के भीतर या आसपास के क्षेत्रों में मध्यम लोड के लिए उपयुक्त है। उपलब्धता जांचने के लिए कृपया अपना पिनकोड साझा करें।"
  },
  
  "bolero": {
    en: "Bolero Pickup (MaXX) is strong and suitable for various terrains. It's good for rural and semi-urban areas where roads might not be in the best condition. Please share your pincode to check availability.",
    hi: "बोलेरो पिकअप (MaXX) मजबूत है और विभिन्न इलाकों के लिए उपयुक्त है। यह ग्रामीण और अर्ध-शहरी क्षेत्रों के लिए अच्छा है जहां सड़कें सबसे अच्छी स्थिति में नहीं हो सकती हैं। उपलब्धता जांचने के लिए कृपया अपना पिनकोड साझा करें।"
  },
  
  "407": {
    en: "Tata 407 is popular for medium-size commercial transport. It can carry loads up to 2.5 tons and is suitable for longer distances. Please share your pincode to check availability.",
    hi: "टाटा 407 मध्यम आकार के वाणिज्यिक परिवहन के लिए लोकप्रिय है। यह 2.5 टन तक का भार ले जा सकता है और लंबी दूरी के लिए उपयुक्त है। उपलब्धता जांचने के लिए कृपया अपना पिनकोड साझा करें।"
  },
  
  "get contact": {
    en: "To get contact details of vehicle owners, please first tell me what type of vehicle you need and your location's pincode. Once I confirm availability, you can find the contact details in the 'Find Vehicle' section on our homepage.",
    hi: "वाहन मालिकों के संपर्क विवरण प्राप्त करने के लिए, कृपया पहले मुझे बताएं कि आपको किस प्रकार के वाहन की आवश्यकता है और आपके स्थान का पिनकोड क्या है। एक बार जब मैं उपलब्धता की पुष्टि कर लूं, तो आप हमारे होमपेज पर 'Find Vehicle' सेक्शन में संपर्क विवरण पा सकते हैं।"
  },
  
  "vehicle number": {
    en: "To get vehicle numbers and owner details, please first tell me what type of vehicle you need and your location's pincode. Once I confirm availability, you can find all details including contact numbers in the 'Find Vehicle' section on our homepage.",
    hi: "वाहन नंबर और मालिक विवरण प्राप्त करने के लिए, कृपया पहले मुझे बताएं कि आपको किस प्रकार के वाहन की आवश्यकता है और आपके स्थान का पिनकोड क्या है। एक बार जब मैं उपलब्धता की पुष्टि कर लूं, तो आप हमारे होमपेज पर 'Find Vehicle' सेक्शन में संपर्क नंबर सहित सभी विवरण पा सकते हैं।"
  },
  
  // Add Tow-Truck responses
  "tow truck": {
    en: "Tow Trucks are specialized vehicles designed to move disabled, improperly parked, or otherwise indisposed vehicles. They're perfect for vehicle recovery, breakdown assistance, and moving damaged vehicles after accidents. Please share your pincode to check availability.",
    hi: "टो ट्रक विशेष वाहन हैं जो खराब, गलत तरीके से पार्क किए गए, या अन्यथा अक्षम वाहनों को स्थानांतरित करने के लिए डिज़ाइन किए गए हैं। वे वाहन रिकवरी, ब्रेकडाउन सहायता और दुर्घटनाओं के बाद क्षतिग्रस्त वाहनों को स्थानांतरित करने के लिए एकदम सही हैं। उपलब्धता जांचने के लिए कृपया अपना पिनकोड साझा करें।"
  },
  "car breakdown": {
    en: "For a car breakdown, I recommend a Tow Truck. They have specialized equipment to safely transport your vehicle to a repair shop. They can handle various types of breakdowns and ensure your vehicle is moved without causing additional damage. Please share your pincode to check availability.",
    hi: "कार ब्रेकडाउन के लिए, मैं टो ट्रक की सलाह देता हूं। उनके पास आपके वाहन को सुरक्षित रूप से रिपेयर शॉप तक ले जाने के लिए विशेष उपकरण होते हैं। वे विभिन्न प्रकार के ब्रेकडाउन को संभाल सकते हैं और सुनिश्चित करते हैं कि आपका वाहन अतिरिक्त नुकसान के बिना स्थानांतरित किया जाए। उपलब्धता जांचने के लिए कृपया अपना पिनकोड साझा करें।"
  },
  "vehicle recovery": {
    en: "For vehicle recovery after a breakdown or accident, a Tow Truck is your best option. They have winches, flatbeds, and hook systems to safely recover and transport vehicles of various sizes. Please share your pincode to check for available Tow Trucks in your area.",
    hi: "ब्रेकडाउन या दुर्घटना के बाद वाहन की रिकवरी के लिए, टो ट्रक आपका सबसे अच्छा विकल्प है। उनके पास विभिन्न आकारों के वाहनों को सुरक्षित रूप से रिकवर और ट्रांसपोर्ट करने के लिए विंच, फ्लैटबेड और हुक सिस्टम होते हैं। अपने क्षेत्र में उपलब्ध टो ट्रक की जांच के लिए कृपया अपना पिनकोड साझा करें।"
  },
  "accident recovery": {
    en: "For recovering vehicles after an accident, Tow Trucks are essential. They have specialized equipment to safely handle damaged vehicles without causing further damage. For severe accidents, flatbed tow trucks are recommended as they provide the most secure transport. Please share your pincode to check availability.",
    hi: "दुर्घटना के बाद वाहनों की रिकवरी के लिए, टो ट्रक आवश्यक हैं। उनके पास क्षतिग्रस्त वाहनों को बिना और नुकसान पहुंचाए सुरक्षित रूप से संभालने के लिए विशेष उपकरण होते हैं। गंभीर दुर्घटनाओं के लिए, फ्लैटबेड टो ट्रक की सिफारिश की जाती है क्योंकि वे सबसे सुरक्षित परिवहन प्रदान करते हैं। उपलब्धता जांचने के लिए कृपया अपना पिनकोड साझा करें।"
  },
  
  // Add greeting responses
  "hello": {
    en: "Hello! How can I help you today? Need assistance finding the right transport vehicle for your needs?",
    hi: "नमस्ते! आज मैं आपकी कैसे मदद कर सकता हूं? क्या आपको अपनी जरूरतों के लिए सही परिवहन वाहन खोजने में सहायता चाहिए?"
  },
  "hi": {
    en: "Hi there! How can I assist you today? Looking for a specific type of transport vehicle?",
    hi: "नमस्ते! आज मैं आपकी क्या मदद कर सकता हूं? क्या आप किसी विशेष प्रकार के परिवहन वाहन की तलाश कर रहे हैं?"
  },
  "hey": {
    en: "Hey! I'm here to help you find the perfect transport solution. What are you looking for today?",
    hi: "अरे! मैं आपको सही परिवहन समाधान खोजने में मदद करने के लिए यहां हूं। आज आप क्या खोज रहे हैं?"
  },
  "jai shree ram": {
    en: "Jai Shree Ram! How may I assist you with your transport needs today?",
    hi: "जय श्री राम! आज मैं आपकी परिवहन आवश्यकताओं में कैसे सहायता कर सकता हूं?"
  },
  "good morning": {
    en: "Good morning! How can I help you find the right transport vehicle today?",
    hi: "सुप्रभात! आज मैं आपको सही परिवहन वाहन खोजने में कैसे मदद कर सकता हूं?"
  },
  "good afternoon": {
    en: "Good afternoon! How may I assist you with your transport requirements today?",
    hi: "नमस्कार! आज मैं आपकी परिवहन आवश्यकताओं में कैसे सहायता कर सकता हूं?"
  },
  "good evening": {
    en: "Good evening! How can I help you with your transport needs today?",
    hi: "शुभ संध्या! आज मैं आपकी परिवहन जरूरतों में कैसे मदद कर सकता हूं?"
  },
  "how are you": {
    en: "I'm doing great! How about you? How can I assist you today?",
    hi: "मैं अच्छा हूं! आप कैसे हैं? आज मैं आपकी कैसे सहायता कर सकता हूं?"
  },
  "what are you doing": {
    en: "I'm here to help you find the perfect transport solution. What are you looking for today?",
    hi: "मैं यहां हूं आपको सही परिवहन समाधान खोजने में मदद करने के लिए। आज आप क्या खोज रहे हैं?"
  },

  // General queries
  "how to register vehicle": {
    en: "To register your vehicle, click <a href=\"register\" target=\"_blank\" rel=\"noopener noreferrer\">Register Now</a> on our homepage. Fill your owner and vehicle details and submit. Our team will verify and list your vehicle. Need help? Watch the tutorial: <a href=\"https://youtu.be/FREi_Lb4gWY?si=pA-zy8ryJQ_TEgzC\" target=\"_blank\" rel=\"noopener noreferrer\">YouTube video</a>.",
    hi: "अपना वाहन रजिस्टर करने के लिए <a href=\"register\" target=\"_blank\" rel=\"noopener noreferrer\">Register Now</a> पर क्लिक करें। फ़ॉर्म में मालिक और वाहन विवरण भरें और सबमिट करें। हमारी टीम वेरीफाई करके आपकी गाड़ी लिस्ट कर देगी। मदद चाहिए? यह ट्यूटोरियल देखें: <a href=\"https://youtu.be/FREi_Lb4gWY?si=pA-zy8ryJQ_TEgzC\" target=\"_blank\" rel=\"noopener noreferrer\">YouTube वीडियो</a>।"
  },
  "how to list vehicle": {
    en: "To list your vehicle for free: 1) Open <a href=\"register\" target=\"_blank\" rel=\"noopener noreferrer\">Register</a>, 2) Select your role and vehicle category, 3) Fill owner and vehicle details, 4) Submit. After quick verification, your vehicle appears in search for your pincode. Quick demo: <a href=\"https://youtu.be/FREi_Lb4gWY?si=pA-zy8ryJQ_TEgzC\" target=\"_blank\" rel=\"noopener noreferrer\">YouTube video</a>.",
    hi: "अपनी गाड़ी फ्री में लिस्ट करने के लिए: 1) <a href=\"register\" target=\"_blank\" rel=\"noopener noreferrer\">Register</a> खोलें, 2) अपना रोल और वाहन कैटेगरी चुनें, 3) मालिक और वाहन विवरण भरें, 4) सबमिट करें। त्वरित वेरीफिकेशन के बाद आपकी गाड़ी आपके पिनकोड की सर्च में दिखेगी। डेमो: <a href=\"https://youtu.be/FREi_Lb4gWY?si=pA-zy8ryJQ_TEgzC\" target=\"_blank\" rel=\"noopener noreferrer\">YouTube वीडियो</a>।"
  },
  "how this website works": {
    en: "Herapherigoods is a free listing website. How it works: 1) Owners <a href=\"register\" target=\"_blank\" rel=\"noopener noreferrer\">register</a> and list vehicles with pincode, 2) Customers search by vehicle type + pincode on the homepage Find Vehicle, 3) We show matches with owner contacts—no commission. Explore <a href=\"vehicles\" target=\"_blank\" rel=\"noopener noreferrer\">vehicles</a> or <a href=\"register\" target=\"_blank\" rel=\"noopener noreferrer\">list yours</a>.",
    hi: "हेराफेरी गुड्स एक फ्री लिस्टिंग वेबसाइट है। ऐसे काम करती है: 1) मालिक <a href=\"register\" target=\"_blank\" rel=\"noopener noreferrer\">रजिस्टर</a> करके वाहन पिनकोड के साथ लिस्ट करते हैं, 2) ग्राहक होमपेज के Find Vehicle पर वाहन प्रकार + पिनकोड से खोजते हैं, 3) हम मिलते-जुलते विकल्प और मालिक का संपर्क दिखाते हैं—कोई कमीशन नहीं। <a href=\"vehicles\" target=\"_blank\" rel=\"noopener noreferrer\">Vehicles</a> देखें या <a href=\"register\" target=\"_blank\" rel=\"noopener noreferrer\">अपनी गाड़ी लिस्ट करें</a>।"
  },
  "contact details": {
    en: "You can contact us at " + SUPPORT_EMAIL + " or whatsapp us at +91 9411292852. Our Contact Hours are Monday to Sunday, 9:00 AM to 8:00 PM.",
    hi: "आप हमसे " + SUPPORT_EMAIL + " पर संपर्क कर सकते हैं या हमें +91 9411292852 पर whatsapp कर सकते हैं। हमारे संपर्क समय सोमवार से रविवार, सुबह 9:00 बजे से शाम 8:00 बजे तक है।"
  },
  "what is herapheri goods": {
    en: "Herapherigoods is a free listing platform that directly connects transport vehicle owners and customers without any third-party commission. We help you find the right transport vehicle for your needs, from small deliveries to heavy-duty shifting.",
    hi: "हेराफेरी गुड्स एक फ्री लिस्टिंग प्लेटफॉर्म है जो ट्रांसपोर्ट वाहन मालिकों और ग्राहकों को बिना किसी थर्ड-पार्टी कमीशन के सीधे जोड़ता है। हम आपको छोटी डिलीवरी से लेकर भारी शिफ्टिंग तक, आपकी जरूरतों के लिए सही ट्रांसपोर्ट वाहन खोजने में मदद करते हैं।"
  },
  "driver contact number": {
    en: "Sorry, I can't provide any direct details right now. Please first let me know what type of vehicle you need and your location's pincode so I can assist you properly.",
    hi: "माफ़ कीजिए, मैं अभी कोई सीधी जानकारी नहीं दे सकता। कृपया पहले मुझे बताएं कि आपको किस प्रकार के वाहन की आवश्यकता है और आपके स्थान का पिनकोड क्या है ताकि मैं आपकी सही तरीके से सहायता कर सकूं।"
  },
  "booking": {
    en: "Sorry, This platform didn't provide any booking facility, We provide only contact details of the vehicle owner in your location. Please let me know your transport need and I'll try to suggest the right vehicle for you.",
    hi: "क्षमा करें, मैं इस समय इसमें आपकी मदद नहीं कर सकता। कृपया मुझे अपनी परिवहन आवश्यकता बताएं और मैं आपके लिए सही वाहन सुझाने का प्रयास करूंगा।"
  },
  "payment": {
    en: "Sorry, This platform didn't provide any payment facility, We provide only contact details of the vehicle owner in your location. Please let me know your transport need and I'll try to suggest the right vehicle for you.",
    hi: "क्षमा करें, मैं इस समय इसमें आपकी मदद नहीं कर सकता। कृपया मुझे अपनी परिवहन आवश्यकता बताएं और मैं आपके लिए सही वाहन सुझाने का प्रयास करूंगा।"
  },
  "tracking": {
    en: "Sorry, This platform didn't provide any tracking facility, We provide only contact details of the vehicle owner in your location. Please let me know your transport need and I'll try to suggest the right vehicle for you.",
    hi: "क्षमा करें, मैं इस समय इसमें आपकी मदद नहीं कर सकता। कृपया मुझे अपनी परिवहन आवश्यकता बताएं और मैं आपके लिए सही वाहन सुझाने का प्रयास करूंगा।"
  },
  
  // Frequently asked questions
  "मेरे सामान के लिए कौनसा वाहन सही रहेगा?": {
    en: "To suggest the right vehicle for your goods, I need to know what type of goods you have, their quantity, and the distance they need to be transported. For general household items, a mini truck or Tata Ace is usually suitable.",
    hi: "आपके सामान के लिए सही वाहन सुझाने के लिए, मुझे जानना होगा कि आपके पास किस प्रकार का सामान है, उसकी मात्रा कितनी है, और उन्हें कितनी दूरी तक ले जाना है। सामान्य घरेलू सामान के लिए, एक मिनी ट्रक या टाटा एस आमतौर पर उपयुक्त होता है।"
  },
  "फर्नीचर शिफ्टिंग के लिए कौनसा वाहन लेना चाहिए?": {
    en: "For furniture shifting, I recommend Packers & Movers service or a Tata 407 truck. For a small amount of furniture, a Tata Ace (Chhota Hathi) would be sufficient. For a complete home shifting, a mini truck or larger vehicle would be better.",
    hi: "फर्नीचर शिफ्टिंग के लिए, मैं पैकर्स एंड मूवर्स सेवा या टाटा 407 ट्रक की सलाह देता हूं। थोड़े फर्नीचर के लिए, टाटा एस (छोटा हाथी) पर्याप्त होगा। पूरे घर की शिफ्टिंग के लिए, एक मिनी ट्रक या बड़े वाहन बेहतर होंगे।"
  },
  "आइसक्रीम डिलीवरी के लिए कौनसा वाहन बेस्ट है?": {
    en: "For ice cream delivery, refrigerated vans or trucks are best as they maintain the required freezing temperature. For smaller quantities and short distances, a smaller refrigerated van would be sufficient.",
    hi: "आइसक्रीम डिलीवरी के लिए, रेफ्रिजरेटेड वैन या ट्रक सबसे अच्छे हैं क्योंकि वे आवश्यक फ्रीजिंग तापमान बनाए रखते हैं। कम मात्रा और कम दूरी के लिए, एक छोटी रेफ्रिजरेटेड वैन पर्याप्त होगी।"
  },
  "हेराफेरी गुड्स क्या है?": {
    en: "Herapherigoods is a free listing platform that directly connects transport vehicle owners and customers without any third-party commission. We help you find the right transport vehicle for your needs, from small deliveries to heavy-duty shifting.",
    hi: "हेराफेरी गुड्स एक फ्री लिस्टिंग प्लेटफॉर्म है जो ट्रांसपोर्ट वाहन मालिकों और ग्राहकों को बिना किसी थर्ड-पार्टी कमीशन के सीधे जोड़ता है। हम आपको छोटी डिलीवरी से लेकर भारी शिफ्टिंग तक, आपकी जरूरतों के लिए सही ट्रांसपोर्ट वाहन खोजने में मदद करते हैं।"
  },
  "वाहन रजिस्टर कैसे करें?": {
    en: "To register your vehicle, click <a href=\"register\" target=\"_blank\" rel=\"noopener noreferrer\">Register Now</a> on our homepage. Fill owner and vehicle details, then submit. If you face any issues, please watch: <a href=\"https://youtu.be/FREi_Lb4gWY?si=pA-zy8ryJQ_TEgzC\" target=\"_blank\" rel=\"noopener noreferrer\">YouTube video</a>.",
    hi: "अपना वाहन रजिस्टर करने के लिए होमपेज पर <a href=\"register\" target=\"_blank\" rel=\"noopener noreferrer\">Register Now</a> पर क्लिक करें। फ़ॉर्म में अपनी और अपने वाहन की जानकारी भरें और सबमिट करें। अधिक जानकारी के लिए यह वीडियो देखें: <a href=\"https://youtu.be/FREi_Lb4gWY?si=pA-zy8ryJQ_TEgzC\" target=\"_blank\" rel=\"noopener noreferrer\">YouTube वीडियो</a>।"
  },
  "गाड़ी कैसे लिस्ट करें?": {
    en: "To list your vehicle: Open <a href=\"register\" target=\"_blank\" rel=\"noopener noreferrer\">Register</a> on the homepage, choose your role and vehicle category, fill owner and vehicle details, then submit. After verification, your vehicle will be visible to users in your pincode.",
    hi: "गाड़ी लिस्ट करने के लिए: होमपेज पर <a href=\"register\" target=\"_blank\" rel=\"noopener noreferrer\">Register</a> खोलें, अपना रोल और वाहन श्रेणी चुनें, मालिक और वाहन विवरण भरें और सबमिट करें। वेरीफिकेशन के बाद आपकी गाड़ी आपके पिनकोड के यूज़र्स को दिखेगी।"
  },
  "यह वेबसाइट कैसे काम करती है?": {
    en: "Herapherigoods works in 3 steps: 1) Owners <a href=\"register\" target=\"_blank\" rel=\"noopener noreferrer\">list vehicles</a>, 2) Customers search by vehicle type + pincode, 3) We show matches with contact details. No booking/payment—contact owners directly. Start here: <a href=\"vehicles\" target=\"_blank\" rel=\"noopener noreferrer\">Find Vehicle</a> or <a href=\"register\" target=\"_blank\" rel=\"noopener noreferrer\">Register</a>.",
    hi: "हेराफेरी गुड्स 3 स्टेप में काम करता है: 1) मालिक <a href=\"register\" target=\"_blank\" rel=\"noopener noreferrer\">वाहन लिस्ट</a> करते हैं, 2) ग्राहक वाहन प्रकार + पिनकोड से खोजते हैं, 3) हम मिलते-जुलते विकल्प और कॉन्टैक्ट दिखाते हैं। साइट पर बुकिंग/पेमेंट नहीं—सीधे मालिक से संपर्क करें। शुरुआत करें: <a href=\"vehicles\" target=\"_blank\" rel=\"noopener noreferrer\">Find Vehicle</a> या <a href=\"register\" target=\"_blank\" rel=\"noopener noreferrer\">Register</a>।"
  },
  

  // New responses for vehicle with pincode requests
  "need_pincode": {
    en: "Thank you for your interest in {vehicle_type}. To find available vehicles near you, I also need your location's pincode. Could you please share your pincode?",
    hi: "आपकी {vehicle_type} में रुचि के लिए धन्यवाद। आपके आस-पास उपलब्ध वाहनों को खोजने के लिए, मुझे आपके स्थान का पिनकोड भी चाहिए। क्या आप अपना पिनकोड साझा कर सकते हैं?"
  },
  "vehicle_found": {
    en: "Great news! I found {count} {vehicle_type}(s) available at pincode {pincode}. To view the details of these vehicles, please go to the 'Find Vehicle' section on our homepage, select '{vehicle_type}' from the dropdown menu, and enter your pincode. You'll see all available options with contact details.",
    hi: "अच्छी खबर! मुझे पिनकोड {pincode} पर {count} {vehicle_type} उपलब्ध मिला है। इन वाहनों के विवरण देखने के लिए, कृपया हमारे होमपेज पर 'Find Vehicle' सेक्शन पर जाएं, ड्रॉपडाउन मेनू से '{vehicle_type}' चुनें, और अपना पिनकोड दर्ज करें। आप संपर्क विवरण के साथ सभी उपलब्ध विकल्प देखेंगे।"
  },
  "vehicle_not_found": {
    en: "I'm sorry, but there is no {vehicle_type} available at pincode {pincode} at the moment. Please try checking for other vehicle types or try again later as our listings are updated regularly.",
    hi: "मुझे खेद है, लेकिन मुझे इस समय पिनकोड {pincode} पर कोई {vehicle_type} उपलब्ध नहीं मिला। कृपया अन्य वाहन प्रकारों की जांच करें या बाद में फिर से प्रयास करें क्योंकि हमारी सूची नियमित रूप से अपडेट की जाती है।"
  },
  "pincode_detected": {
    en: "Thanks for sharing your pincode {pincode}. Let me check for {vehicle_type} availability in your area...",
    hi: "आपका पिनकोड {pincode} साझा करने के लिए धन्यवाद। मुझे आपके क्षेत्र में {vehicle_type} की उपलब्धता की जांच करने दें..."
  }
};

// Frequently asked questions in Hindi – safely initialize without ReferenceError
(function(){
  const existing = (typeof window.frequentlyAskedQuestions !== 'undefined') ? window.frequentlyAskedQuestions : undefined;
  if (!Array.isArray(existing) || existing.length === 0) {
    window.frequentlyAskedQuestions = [
      "मेरे सामान के लिए कौनसा वाहन सही रहेगा?",
      "फर्नीचर शिफ्टिंग के लिए कौनसा वाहन लेना चाहिए?",
      "आइसक्रीम डिलीवरी के लिए कौनसा वाहन बेस्ट है?",
      "हेराफेरी गुड्स क्या है?",
      "वाहन रजिस्टर कैसे करें?",
      "गाड़ी कैसे लिस्ट करें?",
      "यह वेबसाइट कैसे काम करती है?"
    ];
  }
})();

// Function to store conversation in the database using Spring Boot API
async function storeConversation(sessionId, userMessage, aiResponse, vehicleType, pincode, isSuccessful) {
  try {
    const response = await fetch(`${API_BASE_URL}/conversation`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({
        sessionId: sessionId,
        userMessage: userMessage,
        aiResponse: aiResponse,
        vehicleType: vehicleType || null,
        pincode: pincode || null,
        isSuccessful: isSuccessful || false
      })
    });
    
    if (!response.ok) {
      
      return false;
    }
    
    return true;
  } catch (err) {
    
    return false;
  }
}

// Function to check vehicle availability using Spring Boot API
async function checkVehicleAvailability(vehicleType, pincode) {
  // Try AI availability endpoint first
  try {
    const aiBase = API_BASE_URL;
    const response = await fetch(`${aiBase}/vehicle-availability?vehicleType=${encodeURIComponent(vehicleType)}&pincode=${encodeURIComponent(pincode)}`);
    if (response.ok) {
      const data = await response.json();
      // If AI endpoint positively confirms availability, return it.
      if (data && (data.exists === true || (typeof data.count === 'number' && data.count > 0))) {
        return {
          exists: true,
          count: data.count || 1,
          normalizedVehicleType: data.normalizedVehicleType || vehicleType
        };
      }
      // Otherwise, fall through to the general search fallback below
      
    }
  } catch (err) {
    
  }

  // Fallback to the same search endpoint used by vehicles page
  try {
    const base = (window.API_BASE_URL || 'https://api.herapherigoods.in').replace(/\/+$/,'') + '/api';
    const apiUrl = `${base}/vehicles/search`;
    async function tryType(t) {
      const url = `${apiUrl}?type=${encodeURIComponent(t)}&pincode=${encodeURIComponent(pincode)}&limit=1`;
      
      const resp = await fetch(url);
      if (!resp.ok) throw new Error(await resp.text());
      const data = await resp.json();
      const count = Array.isArray(data.vehicles) ? data.vehicles.length : (data.count || 0);
      return { exists: count > 0, count, normalizedVehicleType: t };
    }

    // Build candidate types to improve hit rate
    const candidates = [vehicleType];
    const lt = vehicleType.toLowerCase();
    if (lt.includes('12 wheeler')) {
      const twelveTypeVariants = [
        '12 wheeler and onwards',
        '12 Wheeler and onwards',
        '12 wheeler onwards',
        '12 Wheeler'
      ];
      for (const v of twelveTypeVariants) {
        if (!candidates.includes(v)) candidates.push(v);
      }
    }
    if (lt === 'bolero pickup' && !candidates.includes('Bolero Pickup (MaXX)')) {
      candidates.push('Bolero Pickup (MaXX)');
    }
    if (lt.includes('food') && !candidates.includes('Food trucks')) candidates.push('Food trucks');
    if (lt.includes('beverage') && !candidates.includes('Beverage trucks')) candidates.push('Beverage trucks');

    for (const cand of candidates) {
      try {
        const result = await tryType(cand);
        if (result.exists) return result;
      } catch (e) {
        
      }
    }

    // Ultimate fallback: fetch by pincode only and filter client-side (case-insensitive, trimmed)
    try {
      const urlAll = `${apiUrl}?pincode=${encodeURIComponent(pincode)}`;
      
      const respAll = await fetch(urlAll);
      if (respAll.ok) {
        const dataAll = await respAll.json();
        const list = Array.isArray(dataAll.vehicles) ? dataAll.vehicles : [];
        const normalizedCandidates = candidates.map(c => (c || '').toLowerCase().trim());
        const filtered = list.filter(v => {
          const t = (v.vehicleType || v.type || '').toLowerCase().trim();
          return normalizedCandidates.includes(t);
        });
        if (filtered.length > 0) {
          // Prefer the first candidate that matches
          const matchedType = filtered[0].vehicleType || filtered[0].type || candidates[0];
          return { exists: true, count: filtered.length, normalizedVehicleType: matchedType };
        }
      } else {
        
      }
    } catch (e) {
      
    }

    // Return last tried type as normalized if none found
    return { exists: false, count: 0, normalizedVehicleType: candidates[candidates.length - 1] };
  } catch (err) {
    
    return { exists: false, count: 0, normalizedVehicleType: vehicleType };
  }
}

// Language detection function
function detectLanguage(text) {
  // Simple language detection based on character sets
  // This is a basic implementation - for production, consider using a proper language detection library
  
  // Hindi characters Unicode range
  const hindiPattern = /[\u0900-\u097F]/;
  // Tamil characters Unicode range
  const tamilPattern = /[\u0B80-\u0BFF]/;
  // Marathi uses Devanagari script like Hindi
  
  if (hindiPattern.test(text)) {
    return "hi"; // Hindi
  } else if (tamilPattern.test(text)) {
    return "ta"; // Tamil
  } else {
    return "en"; // Default to English
  }
}

// Function to detect vehicle type in a message
function detectVehicleType(message) {
  if (!message) return null;
  
  const lowerMessage = message.toLowerCase();
  
  // Check for exact matches first
  for (const vehicleType of vehicleTypes) {
    if (lowerMessage.includes(vehicleType.toLowerCase())) {
      return vehicleType;
    }
  }
  
  // Common variations that might not be in the vehicleTypes array
  const vehicleTypeMap = {
    // Manual Cart / Thela variations
    "thela": "Manual Cart",
    "thel": "Manual Cart",
    "manual cart": "Manual Cart",
    "rickshaw": "Manual Cart",
    "manual": "Manual Cart",
    "cart": "Manual Cart",
    
    // Auto Loader variations
    "auto": "Auto Loader",
    "cng": "Auto Loader",
    "cng loader": "Auto Loader",
    "auto loader": "Auto Loader",
    "loader": "Auto Loader",
    
    // Tata Ace variations
    "tata ace": "Tata Ace",
    "chhota hathi": "Tata Ace",
    "chota hathi": "Tata Ace",
    "tata": "Tata Ace",
    "ace": "Tata Ace",
    
    // E-Rickshaw variations
    "e-rickshaw": "E-Rickshaw",
    "e rickshaw": "E-Rickshaw",
    "tuk-tuk": "E-Rickshaw",
    "tuk tuk": "E-Rickshaw",
    "e rickshaw loader": "E-Rickshaw Loader (Tuk-Tuk)",
    "e-rickshaw loader": "E-Rickshaw Loader (Tuk-Tuk)",
    
    // Mini Truck variations
    "mini truck": "Mini Truck",
    "eicher": "Mini Truck",
    "canter": "Mini Truck",
    "eicher canter": "Mini Truck (Eicher Canter)",
    "mini": "Mini Truck",
    
    // Vikram Tempo variations
    "vikram": "Vikram Tempo",
    "tempo": "Vikram Tempo",
    "vikram tempo": "Vikram Tempo",
    
    // Bolero Pickup variations
    "bolero": "Bolero Pickup",
    "maxx": "Bolero Pickup",
    "bolero pickup": "Bolero Pickup (MaXX)",
    "pickup": "Bolero Pickup (MaXX)",
    "maxx": "Bolero Pickup (MaXX)",
    
    // Tata 407 variations
    "407": "Tata 407",
    "tata 407": "Tata 407",
    
    // Wheeler variations
    "6 wheeler": "6 wheeler",
    "8 wheeler": "8 wheeler",
    "12 wheeler": "12 wheeler and onwards",
    "12 wheeler and onwards": "12 wheeler and onwards",
    "twelve wheeler": "12 wheeler and onwards",
    "twelve": "12 wheeler and onwards",
    
    // Container Truck variations
    "container": "Container Truck",
    "container truck": "Container Truck",
    
    // Open Body Truck variations
    "open body": "Open Body Truck",
    "open body truck": "Open Body Truck",
    
    // Closed Body Truck variations
    "closed body": "Closed Body Truck",
    "closed body truck": "Closed Body Truck",
    
    // Flatbed Truck variations
    "flatbed": "Flatbed Truck",
    "flat bed": "Flatbed Truck",
    "flatbed truck": "Flatbed Truck",
    
    // JCB variations
    "jcb": "JCB",
    "earth mover": "JCB",
    "excavator": "JCB",
    "excavation": "JCB",
    
    // Crane variations
    "crane": "Crane",
    "lifting": "Crane",
    
    // Trailer Truck variations
    "trailer": "Trailer Truck",
    "trailer truck": "Trailer Truck",
    
    // Tipper Truck variations
    "tipper": "Tipper Truck",
    "dumper": "Tipper Truck",
    "tipper truck": "Tipper Truck",
    "dumper truck": "Tipper Truck",
    
    // Tanker Truck variations
    "tanker": "Tanker Truck",
    "liquid": "Tanker Truck",
    "tanker truck": "Tanker Truck",
    
    // Garbage Truck variations
    "garbage": "Garbage Truck",
    "waste": "Garbage Truck",
    "garbage truck": "Garbage Truck",
    "waste collector": "Garbage Truck",
    
    // Ambulance variations
    "ambulance": "Ambulance",
    "emergency": "Ambulance",
    "medical": "Ambulance",
    "patient": "Ambulance",
    
    // Refrigerated variations
    "refrigerated": "Refrigerated",
    "fridge": "Refrigerated",
    "chilled": "Refrigerated",
    "cold": "Refrigerated",
    "frozen": "Refrigerated",
    "ice cream": "Refrigerated",
    "refrigerated van": "Refrigerated Vans",
    "refrigerated truck": "Refrigerated Trucks",
    "refrigerated vans": "Refrigerated Vans",
    "refrigerated trucks": "Refrigerated Trucks",
    
    // Packers & Movers variations
    "packer": "Packer&Movers",
    "mover": "Packer&Movers",
    "packers": "Packer&Movers",
    "movers": "Packer&Movers",
    "shifting": "Packer&Movers",
    "relocation": "Packer&Movers",
    
    // Food & Beverage variations
    "food": "Food trucks",
    "food truck": "Food trucks",
    "food delivery": "Food trucks",
    "beverage": "Beverage trucks",
    "drink": "Beverage trucks",
    "juice": "Beverage trucks",
    "coffee": "Beverage trucks",
    "tea": "Beverage trucks",
    
    // Parcel Delivery variations
    "parcel": "Parcel Delivery",
    "courier": "Parcel Delivery",
    "delivery": "Parcel Delivery",
    
    // Tow Truck variations
    "tow": "Tow-Truck",
    "tow truck": "Tow-Truck",
    "towing": "Tow-Truck",
    "breakdown": "Tow-Truck",
    "recovery": "Tow-Truck",
    "car breakdown": "Tow-Truck",
    "vehicle recovery": "Tow-Truck",
    "car recovery": "Tow-Truck",
    "vehicle breakdown": "Tow-Truck"
  };
  
  // Check for common variations
  for (const [key, value] of Object.entries(vehicleTypeMap)) {
    if (lowerMessage.includes(key)) {
      
      
      // Find the matching vehicle type from the vehicleTypes array
      for (const vehicleType of vehicleTypes) {
        if (vehicleType.toLowerCase().includes(value.toLowerCase())) {
          
          return vehicleType;
        }
      }
      
      
      
      // If no match found in vehicleTypes, return the mapped value
      return value;
    }
  }
  
  
  return null;
}

// Function to detect pincode in a message
function detectPincode(message) {
  const match = message.match(pincodePattern);
  return match ? match[0] : null;
}

// Function to find the best matching question from training data
function findBestMatch(userQuery) {
  userQuery = userQuery.toLowerCase();
  
  // Check for greetings first
  const greetingPatterns = {
    "hello": ["hello", "helo", "hllo", "hallo"],
    "hi": ["hi", "hii", "hiiii", "hie", "hey", "heyy", "heya"],
    "hey": ["hey", "heyy", "heya", "hay"],
    "jai shree ram": ["namaste", "namaskar", "ram ram", "jai shree ram", "namaskaar"],
    "good morning": ["good morning", "morning", "subah", "suprabhat", "suprabhat", "subh prabhat"],
    "good afternoon": ["good afternoon", "afternoon", "dopahar", "namaskar"],
    "good evening": ["good evening", "evening", "shaam", "sham", "sandhya"]
  };
  
  // Check for greeting patterns
  for (const [key, patterns] of Object.entries(greetingPatterns)) {
    for (const pattern of patterns) {
      if (userQuery === pattern || userQuery.startsWith(pattern + " ")) {
        return key;
      }
    }
  }
  
  // Check for car breakdown and tow truck related queries first (higher priority)
  if (userQuery.includes("car") && (userQuery.includes("broken") || userQuery.includes("breakdown") || userQuery.includes("not working"))) {
    return "car breakdown";
  }
  
  if (userQuery.includes("vehicle") && (userQuery.includes("broken") || userQuery.includes("breakdown") || userQuery.includes("not working"))) {
    return "car breakdown";
  }
  
  if (userQuery.includes("accident") && userQuery.includes("recovery")) {
    return "accident recovery";
  }
  
  if (userQuery.includes("vehicle") && userQuery.includes("recovery")) {
    return "vehicle recovery";
  }
  
  if (userQuery.includes("tow") || userQuery.includes("towing")) {
    return "tow truck";
  }
  
  // Additional patterns for vehicle issues
  const breakdownPatterns = [
    "car is broken", "car broke down", "vehicle broke down", 
    "car is not starting", "vehicle is not starting", "car won't start", 
    "vehicle won't start", "car stopped working", "vehicle stopped working",
    "car needs towing", "vehicle needs towing", "need to tow my car",
    "need to tow my vehicle", "car accident", "vehicle accident",
    "car damaged", "vehicle damaged", "car repair", "vehicle repair",
    "broken car", "broken vehicle", "car issue", "vehicle issue",
    "car problem", "vehicle problem", "car trouble", "vehicle trouble",
    "car not working", "vehicle not working", "car battery dead",
    "vehicle battery dead", "flat tire", "puncture", "engine failure",
    "transmission problem", "car stuck", "vehicle stuck"
  ];
  
  for (const pattern of breakdownPatterns) {
    if (userQuery.includes(pattern)) {
      return "car breakdown";
    }
  }
  
  // Check for vehicle type specific queries
  const vehicleTypeKeywords = {
    "thela": "thela",
    "thel": "thela",
    "rickshaw": "rickshaw",
    "auto": "auto",
    "cng loader": "auto",
    "chhota hathi": "chhota hathi",
    "chota hathi": "chhota hathi",
    "tata ace": "chhota hathi",
    "tuk-tuk": "tuk tuk",
    "tuk tuk": "tuk tuk",
    "e-rickshaw": "tuk tuk",
    "mini truck": "mini truck",
    "eicher": "mini truck",
    "canter": "mini truck",
    "tempo": "tempo",
    "vikram": "tempo",
    "bolero": "bolero",
    "pickup": "bolero",
    "maxx": "bolero",
    "407": "407",
    "tata 407": "407",
    "tow truck": "tow truck",
    "tow-truck": "tow truck",
    "towing": "tow truck",
    "vehicle recovery": "vehicle recovery",
    "car breakdown": "car breakdown",
    "accident recovery": "accident recovery",
    "ambulance": "ambulance",
    "emergency": "ambulance",
    "refrigerated": "refrigerated",
    "fridge": "refrigerated",
    "chilled": "refrigerated",
    "cold": "refrigerated",
    "frozen": "refrigerated",
  };
  
  // Check for vehicle type specific queries first
  for (const [key, value] of Object.entries(vehicleTypeKeywords)) {
    if (userQuery.includes(key)) {
      return value;
    }
  }
  
  // Check for contact number queries
  if ((userQuery.includes("contact") || userQuery.includes("number") || 
       userQuery.includes("phone") || userQuery.includes("details") ||
       userQuery.includes("call") || userQuery.includes("owner")) &&
      !userQuery.includes("pincode") && !userQuery.match(/\b\d{6}\b/)) {
    
    if (userQuery.includes("get") || userQuery.includes("how") || 
        userQuery.includes("where") || userQuery.includes("find")) {
      return "get contact";
    }
    
    if (userQuery.includes("vehicle") || userQuery.includes("plate") || 
        userQuery.includes("registration")) {
      return "vehicle number";
    }
    
    return "driver contact number";
  }
  
  // Check for sensitive queries asking for contact details without pincode
  if ((userQuery.includes("driver") || userQuery.includes("contact") || 
       userQuery.includes("number") || userQuery.includes("location")) && 
      !userQuery.includes("pincode") && !userQuery.match(/\b\d{6}\b/)) {
    return "driver contact number";
  }
  
  // Check for unsupported features
  if (userQuery.includes("booking") || userQuery.includes("book")) {
    return "booking";
  }
  
  if (userQuery.includes("payment") || userQuery.includes("pay") || userQuery.includes("money")) {
    return "payment";
  }
  
  if (userQuery.includes("track") || userQuery.includes("location") || userQuery.includes("where is")) {
    return "tracking";
  }
  
  // Check for direct matches in training data
  for (const question in trainingData) {
    if (userQuery.includes(question)) {
      return question;
    }
  }
  
  // Check for vehicle type queries
  if (userQuery.includes("dairy") || userQuery.includes("milk") || userQuery.includes("curd")) {
    return "which vehicle should i use for dairy products";
  }
  if (userQuery.includes("Transport vehicle") || userQuery.includes("Transport") || userQuery.includes("transport")) {
    return "Transport vehicle";
  }
  
  if (userQuery.includes("ice cream") || userQuery.includes("frozen")) {
    return "ice cream delivery ke liye konsa vehicle best hai";
  }
  
  if (userQuery.includes("furniture") || userQuery.includes("sofa") || userQuery.includes("table") || 
      userQuery.includes("shifting") || userQuery.includes("move") || userQuery.includes("relocation")) {
    return "furniture shifting";
  }
  
  if (userQuery.includes("construction") || userQuery.includes("cement") || userQuery.includes("sand") || 
      userQuery.includes("brick") || userQuery.includes("stone") || userQuery.includes("material")) {
    return "construction material";
  }
  
  if (userQuery.includes("register") || userQuery.includes("signup") || userQuery.includes("join")) {
    return "how to register vehicle";
  }
  // Listing/adding vehicle patterns (EN/HI transliterations)
  if (
    (
      userQuery.includes("list") || userQuery.includes("listing") || userQuery.includes("add") || userQuery.includes("post") ||
      userQuery.includes("लिस्ट") || userQuery.includes("लिस्टिंग") || userQuery.includes("जोड़") || userQuery.includes("ऐड") || userQuery.includes("सूची")
    ) && (
      userQuery.includes("vehicle") || userQuery.includes("gadi") || userQuery.includes("gaadi") || userQuery.includes("गाड़ी") || userQuery.includes("गाडी") || userQuery.includes("वाहन") || userQuery.includes("truck") || userQuery.includes("tempo") || userQuery.includes("rickshaw")
    )
  ) {
    return "how to list vehicle";
  }
  // How the website works / how to use site
  if (
    (userQuery.includes("how") && (userQuery.includes("website") || userQuery.includes("site")) && (userQuery.includes("work") || userQuery.includes("works") || userQuery.includes("use"))) ||
    userQuery.includes("website kaise") || userQuery.includes("kaise kaam") || userQuery.includes("kaise use") || userQuery.includes("वेबसाइट") || userQuery.includes("कैसे काम")
  ) {
    return "how this website works";
  }
  
  if (userQuery.includes("contact") || userQuery.includes("reach") || userQuery.includes("call")) {
    return "contact details";
  }
  
  if (userQuery.includes("what is") || userQuery.includes("about") || userQuery.includes("herapheri")) {
    return "what is herapherigoods";
  }
  
  // Check for exact matches with frequently asked questions
  for (const faq of (window.frequentlyAskedQuestions || [])) {
    if (userQuery === faq.toLowerCase()) {
      return faq;
    }
  }
  
  // If no match found
  return null;
}

// Function to get response based on user query
async function getResponse(userQuery) {
  // Professional fallback if user explicitly asks to converse in Hindi (policy: restrict to English)
  try {
    const qLower = (userQuery || '').toLowerCase();
    const hindiRequestPattern = /(hindi\s*(me|mein|mai)|hindi\s*please|talk\s*in\s*hindi|speak\s*in\s*hindi|can\s*you\s*(talk|speak)\s*(in\s*)?hindi|kya\s*aap\s*hindi|hindi\s*mein\s*baat|hindi\s*me\s*baat)/i;
    if (hindiRequestPattern.test(qLower)) {
      const denialMsg = "Sorry, I’m currently limited to English responses. Please continue in English and I’ll assist you professionally.";
      // Store conversation explicitly
      try { await storeConversation(sessionId, userQuery, denialMsg, null, null, false); } catch(_){ }
      return denialMsg;
    }
  } catch(_){ /* non-critical */ }
  // Check for vehicle type and pincode in the query
  const detectedVehicleType = detectVehicleType(userQuery);
  const detectedPincode = detectPincode(userQuery);
  
  // Update global variables if detected
  if (detectedVehicleType) {
    mentionedVehicleType = detectedVehicleType;
  }
  
  if (detectedPincode) {
    mentionedPincode = detectedPincode;
  }
  
  // If we have both vehicle type and pincode, check availability
  if (mentionedVehicleType && mentionedPincode) {
    // Show processing message first
    const processingResponse = formatResponse(
      "pincode_detected", 
      { pincode: mentionedPincode, vehicle_type: mentionedVehicleType },
      detectLanguage(userQuery)
    );
    
    // Add the processing message to conversation
    addMessageToConversation(processingResponse, 'ai');
    
    // Check vehicle availability
    try {
      const availability = await checkVehicleAvailability(
        mentionedVehicleType, 
        mentionedPincode
      );
      
      // Use the normalized vehicle type from the server
      const normalizedVehicleType = availability.normalizedVehicleType || mentionedVehicleType;
      
      let responseKey = availability.exists ? "vehicle_found" : "vehicle_not_found";
      let response = formatResponse(
        responseKey, 
        { 
          count: availability.count, 
          vehicle_type: normalizedVehicleType,
          pincode: mentionedPincode
        },
        detectLanguage(userQuery)
      );
      
      // Store the conversation in the database
      await storeConversation(
        sessionId,
        userQuery,
        response,
        normalizedVehicleType,
        mentionedPincode,
        availability.exists
      );
      
      return response;
    } catch (error) {
      
      // Store the conversation in the database with error
      await storeConversation(
        sessionId,
        userQuery,
        "Sorry, I encountered an error while checking for vehicles. Please try again later.",
        mentionedVehicleType,
        mentionedPincode,
        false
      );
      
      return "Sorry, I encountered an error while checking for vehicles. Please try again later.";
    }
  }
  
  // If we have vehicle type but no pincode, ask for pincode
  if (mentionedVehicleType && !mentionedPincode) {
    const response = formatResponse(
      "need_pincode", 
      { vehicle_type: mentionedVehicleType },
      detectLanguage(userQuery)
    );
    
    // Store the conversation in the database
    await storeConversation(
      sessionId,
      userQuery,
      response,
      mentionedVehicleType,
      null,
      false
    );
    
    return response;
  }
  
  // Regular response flow
  const matchedQuestion = findBestMatch(userQuery);
  
  if (matchedQuestion && trainingData[matchedQuestion]) {
    const detectedLang = detectLanguage(userQuery);
    const response = trainingData[matchedQuestion][detectedLang] || trainingData[matchedQuestion].en;
    
    // Store the conversation in the database
    await storeConversation(
      sessionId,
      userQuery,
      response,
      mentionedVehicleType,
      mentionedPincode,
      true
    );
    
    return response;
  }
  
  // Default response if no match found
  const detectedLang = detectLanguage(userQuery);
  let defaultResponse;
  
  if (detectedLang === "hi") {
    defaultResponse = "मुझे आपका प्रश्न समझ नहीं आया। कृपया अपनी परिवहन आवश्यकता के बारे में अधिक जानकारी दें, जैसे कि आपको किस प्रकार का वाहन चाहिए और किस उद्देश्य के लिए।";
  } else {
    defaultResponse = "I didn't understand your question. Please provide more information about your transport need, such as what type of vehicle you need and for what purpose.";
  }
  
  // Store the conversation in the database
  await storeConversation(
    sessionId,
    userQuery,
    defaultResponse,
    mentionedVehicleType,
    mentionedPincode,
    false
  );
  
  return defaultResponse;
}

// Function to format responses with placeholders
function formatResponse(responseKey, data, language) {
  if (!trainingData[responseKey]) {
    return "Sorry, I couldn't process your request.";
  }
  
  let response = trainingData[responseKey][language] || trainingData[responseKey].en;
  
  // Replace placeholders with actual data
  for (const [key, value] of Object.entries(data)) {
    response = response.replace(`{${key}}`, value);
  }
  
  return response;
}

// Init scheduling: defer assistant setup to idle or first interaction
let __aiInitDone = false;
function initAssistantOnce(){
  if (__aiInitDone) return; __aiInitDone = true;
  createAIAssistantModal();

  // Reset the FAQ collapsed state
  faqManuallyCollapsed = false;

  // Add event listeners after creating the modal
  const icon = document.getElementById('ai-assistant-icon');
  const closeBtn = document.getElementById('ai-assistant-close');
  const form = document.getElementById('ai-assistant-form');
  if (icon) icon.addEventListener('click', () => { ensureIconVideoLoaded(); toggleAIAssistant(); });
  if (closeBtn) closeBtn.addEventListener('click', toggleAIAssistant);
  if (form) form.addEventListener('submit', handleUserQuery);

  // Add event listeners for frequently asked questions
  document.querySelectorAll('.ai-assistant-faq-item').forEach(item => {
    item.addEventListener('click', function() {
      const input = document.getElementById('ai-assistant-input');
      if (input) input.value = this.textContent;
      handleUserQuery(new Event('submit'));
    });
  });

  // Add event listener for FAQ toggle
  const faqToggle = document.getElementById('faq-toggle-btn');
  if (faqToggle) faqToggle.addEventListener('click', toggleFAQSection);

  // Input typing shows FAQs
  const inputField = document.getElementById('ai-assistant-input');
  if (inputField) {
    inputField.addEventListener('input', function(){ if (this.value.trim() !== '') showFAQsOnInput(); });
  }

  // Show welcome message bubble after a short delay
  setTimeout(showWelcomeMessage, 1000);
}

// Schedule initialization
document.addEventListener('DOMContentLoaded', function(){
  const start = () => initAssistantOnce();
  if ('requestIdleCallback' in window) {
    requestIdleCallback(start, { timeout: 4000 });
  } else {
    setTimeout(start, 3000);
  }
  ['pointerdown','touchstart','keydown','click'].forEach(ev => {
    window.addEventListener(ev, () => initAssistantOnce(), { once: true, passive: true });
  });
});

// Function to create the AI Assistant modal
function createAIAssistantModal() {
  // Create the AI Assistant icon
  const assistantIcon = document.createElement('div');
  assistantIcon.id = 'ai-assistant-icon';
  assistantIcon.innerHTML = '<video autoplay loop muted playsinline preload="none" class="ai-icon-img"><source data-src="attached_assets/animation/AI-assistant.webm" type="video/webm"></video>';
  document.body.appendChild(assistantIcon);

  // Lazy load the icon video when visible or on first click
  try {
    const ensure = () => ensureIconVideoLoaded();
    if ('IntersectionObserver' in window) {
      const io = new IntersectionObserver((entries) => {
        entries.forEach(e => { if (e.isIntersecting) { ensure(); io.unobserve(assistantIcon); } });
      }, { rootMargin: '200px' });
      io.observe(assistantIcon);
    }
    assistantIcon.addEventListener('click', ensure, { once: true });
  } catch(_){}
  
  // Create the AI Assistant modal
  const assistantModal = document.createElement('div');
  assistantModal.id = 'ai-assistant-modal';
  assistantModal.classList.add('ai-assistant-hidden');
  
  // Create the modal content
  assistantModal.innerHTML = `
    <div class="ai-assistant-header">
      <div class="ai-assistant-title">
  <img src="attached_assets/images/ai.webp" alt="Herapheri Assistant" title="Herapheri Assistant" class="ai-assistant-logo">
        <h3>Virtual Assistant <span class="beta-tag">BETA</span></h3>
        <div class="language-support">Supports: Hindi & English</div>
      </div>
      <button id="ai-assistant-close" aria-label="Close assistant" title="Close">
        <svg viewBox="0 0 24 24" width="1em" height="1em" aria-hidden="true" focusable="false" style="fill:currentColor;">
          <path d="M6 6l12 12M18 6L6 18" stroke="currentColor" stroke-width="2" fill="none" stroke-linecap="round"/>
        </svg>
      </button>
    </div>
    <div class="ai-assistant-conversation" id="ai-assistant-conversation">
      <div class="ai-assistant-message ai-message">
        <div class="ai-assistant-avatar">
          <img src="attached_assets/images/ai.webp" alt="AI Assistant" title="AI Assistant" class="ai-icon-img">
        </div>
        <div class="ai-assistant-bubble">
          Hii! How can I help you? Need any type of transport vehicle? I will help you to find the best vehicle for your needs.
        </div>
      </div>
    </div>
    <div class="ai-assistant-faq-container">
      <div class="faq-header">
        <h4 id="ai-faq-heading">अक्सर पूछे जाने वाले प्रश्न:</h4>
        <button id="faq-toggle-btn" class="faq-toggle-btn" aria-label="Toggle FAQ section" aria-expanded="true" aria-controls="ai-assistant-faq">
          <svg id="ai-faq-chevron" viewBox="0 0 24 24" width="1em" height="1em" aria-hidden="true" focusable="false" style="fill:currentColor;">
            <path d="M6 14l6-6 6 6"/>
          </svg>
        </button>
      </div>
      <div class="ai-assistant-faq" id="ai-assistant-faq">
        <ul id="ai-assistant-faq-list">
          ${(window.frequentlyAskedQuestions || []).map(q => `<li class="ai-assistant-faq-item">${escapeHtml(q)}</li>`).join('')}
        </ul>
      </div>
    </div>
    <form id="ai-assistant-form">
      <div class="ai-assistant-input-container">
        <input type="text" id="ai-assistant-input" placeholder="अपना प्रश्न यहां टाइप करें..." aria-label="Type your question">
        <button type="submit" id="ai-assistant-send" aria-label="Send message" title="Send">
          <svg viewBox="0 0 24 24" width="1em" height="1em" aria-hidden="true" focusable="false" style="fill:currentColor;">
            <path d="M2 21l21-9L2 3v7l15 2-15 2z"/>
          </svg>
        </button>
      </div>
    </form>
  `;
  
  document.body.appendChild(assistantModal);
  
  // Create welcome message bubble
  const welcomeBubble = document.createElement('div');
  welcomeBubble.id = 'ai-welcome-bubble';
  welcomeBubble.classList.add('ai-welcome-bubble', 'ai-welcome-hidden');
  welcomeBubble.innerHTML = `
    <div class="welcome-content">
      <p>Hey there! I'm <strong>McQueen</strong> - Need help for finding the right vehicle? Just ask me!</p>
    </div>
    <div class="welcome-arrow"></div>
  `;
  document.body.appendChild(welcomeBubble);
}

// Function to toggle FAQ section
function toggleFAQSection() {
  const faqSection = document.getElementById('ai-assistant-faq');
  const toggleBtn = document.getElementById('faq-toggle-btn');
  const iconWrap = document.getElementById('ai-faq-chevron');
  
  if (faqSection.classList.contains('faq-collapsed')) {
    faqSection.classList.remove('faq-collapsed');
    if (iconWrap) iconWrap.innerHTML = '<path d="M6 14l6-6 6 6"/>';
    toggleBtn.setAttribute('aria-expanded', 'true');
    faqManuallyCollapsed = false; // User expanded the FAQ section
  } else {
    faqSection.classList.add('faq-collapsed');
    if (iconWrap) iconWrap.innerHTML = '<path d="M6 10l6 6 6-6"/>';
    toggleBtn.setAttribute('aria-expanded', 'false');
    faqManuallyCollapsed = true; // User manually collapsed the FAQ section
  }
}

// Function to show FAQs when user starts typing
function showFAQsOnInput() {
  // If the FAQ section was manually collapsed by the user, don't automatically show it
  if (faqManuallyCollapsed) {
    return;
  }
  
  const faqSection = document.getElementById('ai-assistant-faq');
  const toggleBtn = document.getElementById('faq-toggle-btn');
  const icon = toggleBtn.querySelector('i');
  
  // Only show FAQs if they're currently hidden and not manually collapsed
  if (faqSection.classList.contains('faq-collapsed')) {
    faqSection.classList.remove('faq-collapsed');
    icon.classList.remove('fa-chevron-down');
    icon.classList.add('fa-chevron-up');
  }
}

// Function to show welcome message
function showWelcomeMessage() {
  const welcomeBubble = document.getElementById('ai-welcome-bubble');
  welcomeBubble.classList.remove('ai-welcome-hidden');
  
  // Hide the welcome bubble after 6 seconds
  setTimeout(() => {
    welcomeBubble.classList.add('ai-welcome-fade-out');
    
    // Remove from DOM after animation completes
    setTimeout(() => {
      welcomeBubble.remove();
    }, 1000);
  }, 5000);
}

// Function to toggle the AI Assistant modal
function toggleAIAssistant() {
  const modal = document.getElementById('ai-assistant-modal');
  modal.classList.toggle('ai-assistant-hidden');
  
  // If opening the modal, focus on the input field
  if (!modal.classList.contains('ai-assistant-hidden')) {
    document.getElementById('ai-assistant-input').focus();
    
    // Hide welcome bubble if it exists
    const welcomeBubble = document.getElementById('ai-welcome-bubble');
    if (welcomeBubble) {
      welcomeBubble.remove();
    }
  }
}

// Ensure the icon video source is set and started
function ensureIconVideoLoaded(){
  try {
    const v = document.querySelector('#ai-assistant-icon video');
    if (!v) return;
    const srcEl = v.querySelector('source');
    if (srcEl && !srcEl.getAttribute('src')) {
      const ds = srcEl.getAttribute('data-src');
      if (ds) {
        srcEl.setAttribute('src', ds);
        v.load();
      }
    }
    if (v.autoplay && typeof v.play === 'function') {
      const p = v.play(); if (p && typeof p.catch === 'function') p.catch(()=>{});
    }
  } catch(_){ }
}

// Function to handle user query
async function handleUserQuery(event) {
  event.preventDefault();
  
  const inputField = document.getElementById('ai-assistant-input');
  const userQuery = inputField.value.trim();
  
  if (userQuery === '') return;
  
  // Add user message to conversation
  addMessageToConversation(userQuery, 'user');
  
  // Clear input field
  inputField.value = '';
  
  // Show typing indicator
  showTypingIndicator();
  
  // Only show FAQs when user submits a query if they haven't manually collapsed the FAQ section
  if (!faqManuallyCollapsed) {
    const faqSection = document.getElementById('ai-assistant-faq');
    const toggleBtn = document.getElementById('faq-toggle-btn');
    const icon = toggleBtn.querySelector('i');
    
    if (faqSection.classList.contains('faq-collapsed')) {
      faqSection.classList.remove('faq-collapsed');
      icon.classList.remove('fa-chevron-down');
      icon.classList.add('fa-chevron-up');
    }
  }
  
  try {
    // Get AI response - now async
    const aiResponse = await getResponse(userQuery);
    
    // Remove typing indicator
    removeTypingIndicator();
    
    // Add AI response to conversation (if not already added by the processing step)
    if (!aiResponse.includes("Thanks for sharing your pincode")) {
      addMessageToConversation(aiResponse, 'ai');
    }
    
    // Scroll to bottom of conversation
    scrollToBottom();
  } catch (error) {
    
    
    // Remove typing indicator
    removeTypingIndicator();
    
    // Add error message
    addMessageToConversation("Sorry, I encountered an error. Please try again later.", 'ai');
    
    // Scroll to bottom of conversation
    scrollToBottom();
  }
}

// Function to add message to conversation
function addMessageToConversation(message, sender) {
  const conversation = document.getElementById('ai-assistant-conversation');
  
  const messageDiv = document.createElement('div');
  messageDiv.classList.add('ai-assistant-message');
  
  if (sender === 'user') {
    messageDiv.classList.add('user-message');
    // Build DOM safely to avoid injecting raw HTML from user input
    const bubble = document.createElement('div');
    bubble.className = 'ai-assistant-bubble';
    bubble.textContent = message; // sanitize user message
    const avatar = document.createElement('div');
    avatar.className = 'ai-assistant-avatar';
    avatar.innerHTML = '<svg viewBox="0 0 24 24" width="1.2em" height="1.2em" aria-hidden="true" focusable="false" style="fill:currentColor;"><path d="M12 12a5 5 0 1 0-5-5 5 5 0 0 0 5 5zm0 2c-4.42 0-8 2.24-8 5v1h16v-1c0-2.76-3.58-5-8-5z"/></svg>';
    messageDiv.appendChild(bubble);
    messageDiv.appendChild(avatar);
  } else {
    messageDiv.classList.add('ai-message');
    // Allow AI messages to render HTML (for clickable links we control)
    const avatar = document.createElement('div');
    avatar.className = 'ai-assistant-avatar';
  avatar.innerHTML = '<img src="attached_assets/images/ai.webp" alt="AI Assistant" title="AI Assistant" class="ai-icon-img">';
    const bubble = document.createElement('div');
    bubble.className = 'ai-assistant-bubble';
    bubble.textContent = message;
    messageDiv.appendChild(avatar);
    messageDiv.appendChild(bubble);
  }
  
  conversation.appendChild(messageDiv);
  scrollToBottom();
}

// Function to show typing indicator
function showTypingIndicator() {
  const conversation = document.getElementById('ai-assistant-conversation');
  
  const typingDiv = document.createElement('div');
  typingDiv.id = 'ai-assistant-typing';
  typingDiv.classList.add('ai-assistant-message', 'ai-message');
  
  typingDiv.innerHTML = `
    <div class="ai-assistant-avatar">
      <img src="attached_assets/images/ai.webp" alt="AI" class="ai-icon-img">
    </div>
    <div class="ai-assistant-bubble typing-indicator">
      <span></span>
      <span></span>
      <span></span>
    </div>
  `;
  
  conversation.appendChild(typingDiv);
  scrollToBottom();
}

// Function to remove typing indicator
function removeTypingIndicator() {
  const typingIndicator = document.getElementById('ai-assistant-typing');
  if (typingIndicator) {
    typingIndicator.remove();
  }
}

// Function to scroll to bottom of conversation
function scrollToBottom() {
  const conversation = document.getElementById('ai-assistant-conversation');
  conversation.scrollTop = conversation.scrollHeight;
}

// Input typing handler is added within initAssistantOnce()


