// Feedback Carousel (trimmed for production)

document.addEventListener('DOMContentLoaded', function() {
    initFeedbackCarousel();
});

// Initialize the feedback carousel by fetching reviews and setting up auto-scroll
function initFeedbackCarousel() {
    const carouselElement = document.getElementById('feedbackCarousel');
    if (!carouselElement) return;
    
    fetchFeedbackData()
        .then(feedbacks => {
            if (feedbacks && feedbacks.length > 0) {
                const validFeedbacks = feedbacks.filter(feedback => 
                    feedback.rating > 0 && 
                    feedback.reviewText && 
                    feedback.reviewText.trim() !== ''
                );
                
                if (validFeedbacks.length > 0) {
                    populateCarousel(carouselElement, validFeedbacks);
                    setupTrueLoopScroll(carouselElement);
                } else {
                    showNoFeedbackMessage(carouselElement);
                }
            } else {
                showNoFeedbackMessage(carouselElement);
            }
        })
        .catch(error => {
                    
            showErrorMessage(carouselElement);
        });
}

    // Fetch feedback data from the feedback table only (non-signed-in users) via API
function fetchFeedbackData() {
    return new Promise((resolve, reject) => {
        const base = window.API_BASE_URL || 'http://localhost:8080';
        const urlsToTry = [
            `${base}/api/get-feedback`,
            '/api/get-feedback'
        ];
        
        let attemptCount = 0;
        
        function tryNextUrl() {
            if (attemptCount >= urlsToTry.length) {
                resolve(generateMockFeedback());
                return;
            }
            
            const url = urlsToTry[attemptCount];
            attemptCount++;
            
            fetch(url)
                .then(response => {
                    if (!response.ok) {
                        throw new Error(`API returned ${response.status}`);
                    }
                    return response.json();
                })
                .then(data => {
                    // Ensure we received valid data
                    if (Array.isArray(data) && data.length > 0) {
                        // Filter out any invalid entries
                        const validData = data.filter(item => 
                            item && item.rating && 
                            typeof item.rating === 'number' && 
                            item.name && 
                            item.reviewText);
                            
                        if (validData.length > 0) {
                            resolve(validData);
                        } else {
                            tryNextUrl();
                        }
                    } else {
                        tryNextUrl();
                    }
                })
                .catch(error => {
                    tryNextUrl();
                });
        }
        
        // Start trying URLs
        tryNextUrl();
    });
}

// Generate mock feedback data for testing/fallback
function generateMockFeedback() {
    return [
        {
            id: 1,
            name: "Rahul Sharma",
            address: "Delhi, India",
            rating: 5,
            reviewText: "Mujhe jo gaadi chahiye thi apna saaman bhejne ke liye mujhe to mili hi nhi, and ispe lika h ki sabhi type ke gaadivan milegi!",
            source: "feedback"
        },
        {
            id: 2,
            name: "Priya Patel",
            address: "Mumbai, Maharashtra",
            rating: 4,
            reviewText: "Free me Local tempo driver ka number mil gaya aur usne pehli hi baar me phone utha liya. Site thoda aur fast ho to better hai.",
            source: "feedback"
        },
        {
            id: 3,
            name: "Vikrant Singh",
            address: "Jaipur, Rajasthan",
            rating: 3,
            reviewText: "Pehlay baar use kiya, experience thik tha. Kuch numbers busy the but ek se kaam nikal gaya. lekin mujhe tempo chahye tha 3 wheeler car isne mujhe show hi nhi hua, to fir vehicle me problem hoti.",
            source: "feedback"
        },
        {
            id: 4,
            name: "Anjali Gupta",
            address: "Bangalore, Karnataka",
            rating: 5,
            reviewText: "Mujhe to site bhot useful lagi. Mama ke shifting me tempo yahin se mila, and seriously ye platform shifting ki tension ko kam kar deta hai",
            source: "feedback"
        },
        {
            id: 5,
            name: "Mohammed Khan",
            address: "Hyderabad, Telangana",
            rating: 4,
            reviewText: "Acchi service hai, variety of transport options available hai. Ek baar booking karne ke baad driver ko call karna pada but otherwise smooth experience.",
            source: "feedback"
        }
    ];
}

// Shuffle utility (Fisher-Yates)
function shuffleArray(array) {
    let currentIndex = array.length;
    let temporaryValue, randomIndex;
    
    // While there remain elements to shuffle
    while (0 !== currentIndex) {
        // Pick a remaining element
        randomIndex = Math.floor(Math.random() * currentIndex);
        currentIndex -= 1;
        
        // Swap it with the current element
        temporaryValue = array[currentIndex];
        array[currentIndex] = array[randomIndex];
        array[randomIndex] = temporaryValue;
    }
    
    return array;
}

// Populate the carousel with feedback cards
function populateCarousel(carouselElement, feedbacks) {
    // Clear loading indicator
    carouselElement.innerHTML = '';

    // Add original cards first, clones will be added in the setup function
    feedbacks.forEach(feedback => {
        const card = createFeedbackCard(feedback);
        carouselElement.appendChild(card);
    });
}

// Create a feedback card element
function createFeedbackCard(feedback) {
    const card = document.createElement('div');
    card.className = 'feedback-card';
    card.dataset.id = feedback.id;
    
    if (feedback.rating <= 2) {
        card.classList.add('low-rating');
    } else if (feedback.rating === 3) {
        card.classList.add('medium-rating');
    } else {
        card.classList.add('high-rating');
    }
    
    const userInfo = document.createElement('div');
    userInfo.className = 'user-info';
    
    const avatar = document.createElement('div');
    avatar.className = 'user-avatar';
    avatar.textContent = feedback.name ? feedback.name.charAt(0).toUpperCase() : 'U';
    
    if (feedback.rating <= 2) {
        avatar.classList.add('low-rating-avatar');
    } else if (feedback.rating === 3) {
        avatar.classList.add('medium-rating-avatar');
    } else {
        avatar.classList.add('high-rating-avatar');
    }
    
    const userDetails = document.createElement('div');
    userDetails.className = 'user-details';
    
    const userName = document.createElement('div');
    userName.className = 'user-name';
    userName.textContent = feedback.name || 'Anonymous User';
    
    const userLocation = document.createElement('div');
    userLocation.className = 'user-location';
    userLocation.textContent = feedback.address || 'Unknown Location';
    
    userDetails.appendChild(userName);
    userDetails.appendChild(userLocation);
    
    userInfo.appendChild(avatar);
    userInfo.appendChild(userDetails);
    
    const rating = document.createElement('div');
    rating.className = 'rating';
    
    for (let i = 0; i < 5; i++) {
        const star = document.createElement('i');
        star.className = i < feedback.rating ? 'fas fa-star' : 'far fa-star';
        
        if (feedback.rating <= 2) {
            star.classList.add('low-rating-star');
        } else if (feedback.rating === 3) {
            star.classList.add('medium-rating-star');
        } else {
            star.classList.add('high-rating-star');
        }
        
        rating.appendChild(star);
    }
    
    const reviewWrapper = document.createElement('div');
    reviewWrapper.className = 'review-wrapper';
    
    const reviewText = document.createElement('div');
    reviewText.className = 'review-text';
    reviewText.textContent = feedback.reviewText || '';
    
    reviewWrapper.appendChild(reviewText);
    
    card.appendChild(userInfo);
    card.appendChild(rating);
    card.appendChild(reviewWrapper);
    
    card.addEventListener('mouseenter', function() {
        expandCard(card);
    });
    
    card.addEventListener('mouseleave', function() {
        collapseCard(card);
    });
    
    return card;
}

// Expand a card to show full review text
function expandCard(card) {
    const reviewWrapper = card.querySelector('.review-wrapper');
    const reviewText = card.querySelector('.review-text');
    
    if (reviewWrapper && reviewText) {
        reviewText.style.maxHeight = 'none';
        reviewText.style.overflow = 'visible';
        reviewText.classList.add('expanded');
        
        card.style.zIndex = '10';
        card.style.transform = 'scale(1.05)';
        card.style.boxShadow = '0 10px 30px rgba(0, 0, 0, 0.15)';
    }
}

// Collapse a card back to normal size
function collapseCard(card) {
    const reviewText = card.querySelector('.review-text');
    
    if (reviewText) {
        reviewText.style.maxHeight = '';
        reviewText.style.overflow = '';
        reviewText.classList.remove('expanded');
        
        card.style.zIndex = '';
        card.style.transform = '';
        card.style.boxShadow = '';
    }
}

// Set up truly continuous loop scrolling in one direction (right to left)
function setupTrueLoopScroll(carouselElement) {
    // Variables for scroll control
    let scrollPosition = 0;
    const scrollSpeed = 0.6;
    let isPaused = false;
    let animationId = null;
    let lastTimestamp = 0;
    
    // Get container and parent references
    const container = carouselElement.parentElement;
    const containerWidth = container.offsetWidth;
    
    // Get all original cards
    const originalCards = Array.from(carouselElement.querySelectorAll('.feedback-card'));
    if (originalCards.length === 0) return;
    
    // Calculate card dimensions
    const cardWidth = originalCards[0].offsetWidth;
    const cardMargin = parseInt(window.getComputedStyle(originalCards[0]).marginLeft) + 
                       parseInt(window.getComputedStyle(originalCards[0]).marginRight);
    const totalCardWidth = cardWidth + cardMargin;
    
    const existingClones = carouselElement.querySelectorAll('.clone');
    existingClones.forEach(clone => clone.remove());
    
    const minCardsNeeded = Math.ceil((containerWidth * 3) / totalCardWidth);
    const setsNeeded = Math.ceil(minCardsNeeded / originalCards.length);
    
    // Create clones before originals for the infinite loop effect
    for (let i = 0; i < setsNeeded; i++) {
        originalCards.forEach(card => {
            const clone = card.cloneNode(true);
            clone.classList.add('clone');
            
            clone.addEventListener('mouseenter', () => expandCard(clone));
            clone.addEventListener('mouseleave', () => collapseCard(clone));
            
            // Add clones BEFORE the original cards
            carouselElement.insertBefore(clone, carouselElement.firstChild);
        });
    }
    
    for (let i = 0; i < setsNeeded; i++) {
        originalCards.forEach(card => {
            const clone = card.cloneNode(true);
            clone.classList.add('clone');
            
            clone.addEventListener('mouseenter', () => expandCard(clone));
            clone.addEventListener('mouseleave', () => collapseCard(clone));
            
            // Add clones AFTER the original cards
            carouselElement.appendChild(clone);
        });
    }
    
    const totalCards = carouselElement.querySelectorAll('.feedback-card').length;
    const totalWidth = totalCardWidth * totalCards;
    carouselElement.style.width = `${totalWidth}px`;
    
    const setWidth = totalCardWidth * originalCards.length;
    
    scrollPosition = setWidth;
    carouselElement.style.transform = `translateX(-${scrollPosition}px)`;
    
    // Timestamp-based animation
    function animateCarousel(timestamp) {
        if (isPaused) {
            animationId = requestAnimationFrame(animateCarousel);
            return;
        }
        
        // Calculate delta time for smoother animation
        if (!lastTimestamp) lastTimestamp = timestamp;
        const deltaTime = timestamp - lastTimestamp;
        lastTimestamp = timestamp;
        
        // Adjust scroll speed based on deltaTime for consistency
        const frameAdjustedSpeed = (scrollSpeed * deltaTime) / 16.67;
        
        // Move from right to left
        scrollPosition += frameAdjustedSpeed;
        
        carouselElement.style.transform = `translateX(-${scrollPosition}px)`;
        
        if (scrollPosition >= (setWidth * 2)) {
            // Reset to one setWidth exactly, maintaining visual continuity
            scrollPosition = setWidth;
            carouselElement.style.transform = `translateX(-${scrollPosition}px)`;
        }
        
        if (scrollPosition <= 0) {
            scrollPosition = setWidth;
            carouselElement.style.transform = `translateX(-${scrollPosition}px)`;
        }
        
        // Schedule the next frame
        animationId = requestAnimationFrame(animateCarousel);
    }
    
    animationId = requestAnimationFrame(animateCarousel);
    
    const heartbeatInterval = setInterval(() => {
        if (!isPaused && !animationId) {
            lastTimestamp = 0; // Reset timestamp
            animationId = requestAnimationFrame(animateCarousel);
        }
    }, 5000); // Check every 5 seconds
    
    // Pause scrolling when hovering over carousel
    carouselElement.addEventListener('mouseenter', () => {
        isPaused = true;
    });
    
    // Resume scrolling when mouse leaves
    carouselElement.addEventListener('mouseleave', () => {
        isPaused = false;
        if (!animationId) {
            lastTimestamp = 0; // Reset timestamp
            animationId = requestAnimationFrame(animateCarousel);
        }
    });
    
    document.addEventListener('visibilitychange', () => {
        if (document.hidden) {
            // Pause when tab is not visible
            isPaused = true;
        } else {
            // Resume when tab becomes visible again
            isPaused = false;
            if (!animationId) {
                lastTimestamp = 0; // Reset timestamp
                animationId = requestAnimationFrame(animateCarousel);
            }
        }
    });
    
    // Clean up on page unload
    window.addEventListener('beforeunload', () => {
        if (animationId) {
            cancelAnimationFrame(animationId);
        }
        clearInterval(heartbeatInterval);
    });
}

// Display a message when no feedback is available
function showNoFeedbackMessage(carouselElement) {
    carouselElement.innerHTML = '';
    
    const messageCard = document.createElement('div');
    messageCard.className = 'feedback-card no-feedback';
    
    const message = document.createElement('p');
    message.textContent = 'No reviews yet. Be the first to share your experience!';
    
    messageCard.appendChild(message);
    carouselElement.appendChild(messageCard);
}

// Display an error message when feedback cannot be loaded
function showErrorMessage(carouselElement) {
    carouselElement.innerHTML = '';
    
    const errorCard = document.createElement('div');
    errorCard.className = 'feedback-card error';
    
    const message = document.createElement('p');
    message.textContent = 'Sorry, we couldn\'t load the reviews at this time. Please try again later.';
    
    errorCard.appendChild(message);
    carouselElement.appendChild(errorCard);
} 