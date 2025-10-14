# Copy to env.prod.ps1 and fill your secrets
$env:SPRING_PROFILES_ACTIVE = 'prod'

# Database
$env:DB_URL = 'jdbc:postgresql://<host>:5432/postgres?sslmode=require'
$env:DB_USER = 'postgres'
$env:DB_PASSWORD = '<password>'

# Supabase
$env:SUPABASE_URL = 'https://<your-project>.supabase.co'
$env:SUPABASE_KEY = '<service_key>'
$env:SUPABASE_BUCKET = 'vehicle-images'
$env:SUPABASE_PROFILE_BUCKET = 'profile-photos'

# Server
$env:PORT = '8080'

