from pyembroidery import read_pes

def calculate_total_stitches(pes_file_path):
    design = read_pes(pes_file_path)  # Load PES file
    total_stitches = design.count_stitches()  # Get the stitch count from the design
    return total_stitches

pes_file_path = "C:/Users/iamacat/embroidery.pes"
stitch_count = calculate_total_stitches(pes_file_path)
print(f"Total Stitches: {stitch_count}")
