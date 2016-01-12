all: exam.pdf frontpage.pdf
	pdfjam \
	frontpage.pdf \
	exam.pdf \
	--outfile PRCPP-exam-jbec.pdf

exam.pdf: exam.tex
	pdflatex exam.tex
	pdflatex exam.tex
