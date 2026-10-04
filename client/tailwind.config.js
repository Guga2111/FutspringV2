/** @type {import('tailwindcss').Config} */
export default {
  darkMode: ["class"],
  content: ["./index.html", "./src/**/*.{ts,tsx}"],
  theme: {
  	container: {
  		center: true,
  		padding: '2rem',
  		screens: {
  			'2xl': '1400px'
  		}
  	},
  	extend: {
  		colors: {
  			border: 'hsl(var(--border))',
  			input: 'hsl(var(--input))',
  			ring: 'hsl(var(--ring))',
  			background: 'hsl(var(--background))',
  			foreground: 'hsl(var(--foreground))',
  			primary: {
  				DEFAULT: 'hsl(var(--primary))',
  				foreground: 'hsl(var(--primary-foreground))'
  			},
  			secondary: {
  				DEFAULT: 'hsl(var(--secondary))',
  				foreground: 'hsl(var(--secondary-foreground))'
  			},
  			destructive: {
  				DEFAULT: 'hsl(var(--destructive))',
  				foreground: 'hsl(var(--destructive-foreground))',
  				soft: 'hsl(var(--destructive-soft))',
  				muted: 'hsl(var(--destructive-muted))'
  			},
  			muted: {
  				DEFAULT: 'hsl(var(--muted))',
  				foreground: 'hsl(var(--muted-foreground))'
  			},
  			accent: {
  				DEFAULT: 'hsl(var(--accent))',
  				foreground: 'hsl(var(--accent-foreground))'
  			},
  			popover: {
  				DEFAULT: 'hsl(var(--popover))',
  				foreground: 'hsl(var(--popover-foreground))'
  			},
  			card: {
  				DEFAULT: 'hsl(var(--card))',
  				foreground: 'hsl(var(--card-foreground))'
  			},
  			'card-elevated': 'hsl(var(--card-elevated))',
  			'row-hover': 'hsl(var(--row-hover))',
  			'subtle-foreground': 'hsl(var(--subtle-foreground))',
  			'faint-foreground': 'hsl(var(--faint-foreground))',
  			'team-dot-border': 'hsl(var(--team-dot-border))',
  			'star-off': 'hsl(var(--star-off))',
  			avatar: {
  				'1': 'hsl(var(--avatar-1))',
  				'2': 'hsl(var(--avatar-2))',
  				'3': 'hsl(var(--avatar-3))',
  				'4': 'hsl(var(--avatar-4))',
  				'5': 'hsl(var(--avatar-5))',
  				'6': 'hsl(var(--avatar-6))',
  				fallback: 'hsl(var(--avatar-fallback))'
  			},
  			gold: {
  				DEFAULT: 'hsl(var(--gold))',
  				muted: 'hsl(var(--gold-muted))',
  				foreground: 'hsl(var(--gold-foreground))'
  			},
  			silver: 'hsl(var(--silver))',
  			bronze: 'hsl(var(--bronze))',
  			brand: 'hsl(var(--brand))',
  			success: {
  				DEFAULT: 'hsl(var(--success))',
  				strong: 'hsl(var(--success-strong))',
  				soft: 'hsl(var(--success-soft))',
  				muted: 'hsl(var(--success-muted))',
  				border: 'hsl(var(--success-border))'
  			},
  			warning: 'hsl(var(--warning))',
  			info: {
  				DEFAULT: 'hsl(var(--info))',
  				muted: 'hsl(var(--info-muted))'
  			},
  			status: {
  				scheduled: {
  					DEFAULT: 'hsl(var(--status-scheduled))',
  					foreground: 'hsl(var(--status-scheduled-foreground))',
  					dot: 'hsl(var(--status-scheduled-dot))'
  				},
  				live: {
  					DEFAULT: 'hsl(var(--status-live))',
  					foreground: 'hsl(var(--status-live-foreground))',
  					dot: 'hsl(var(--status-live-dot))'
  				}
  			},
  			live: {
  				foreground: 'hsl(var(--live-foreground))',
  				border: 'hsl(var(--live-border))'
  			},
  			chart: {
  				'1': 'hsl(var(--chart-1))',
  				'2': 'hsl(var(--chart-2))'
  			},
  			sidebar: {
  				DEFAULT: 'hsl(var(--sidebar-background))',
  				foreground: 'hsl(var(--sidebar-foreground))',
  				primary: 'hsl(var(--sidebar-primary))',
  				'primary-foreground': 'hsl(var(--sidebar-primary-foreground))',
  				accent: 'hsl(var(--sidebar-accent))',
  				'accent-foreground': 'hsl(var(--sidebar-accent-foreground))',
  				border: 'hsl(var(--sidebar-border))',
  				ring: 'hsl(var(--sidebar-ring))'
  			}
  		},
  		borderRadius: {
  			lg: 'var(--radius)',
  			md: 'calc(var(--radius) - 2px)',
  			sm: 'calc(var(--radius) - 4px)',
  			tile: '14px',
  			hero: '18px'
  		},
  		boxShadow: {
  			menu: '0 10px 30px rgb(0 0 0 / 0.45)',
  			panel: '0 20px 50px rgb(0 0 0 / 0.55)',
  			dialog: '0 24px 60px rgb(0 0 0 / 0.6)',
  			fab: '0 8px 24px rgb(0 0 0 / 0.45)'
  		},
  		keyframes: {
  			'accordion-down': {
  				from: {
  					height: '0'
  				},
  				to: {
  					height: 'var(--radix-accordion-content-height)'
  				}
  			},
  			'accordion-up': {
  				from: {
  					height: 'var(--radix-accordion-content-height)'
  				},
  				to: {
  					height: '0'
  				}
  			}
  		},
  		animation: {
  			'accordion-down': 'accordion-down 0.2s ease-out',
  			'accordion-up': 'accordion-up 0.2s ease-out'
  		}
  	}
  },
  plugins: [require("tailwindcss-animate")],
}
