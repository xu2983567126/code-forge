import moment from 'moment'

export const formatTime = (time: string) => {
  if (!time) return ''
  try {
    return moment(time).format('YYYY-MM-DD HH:mm:ss')
  } catch {
    return time
  }
}